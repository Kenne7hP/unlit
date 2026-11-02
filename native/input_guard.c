// FoldGuard input ownership. No touch coordinates or key streams are logged.
#include <jni.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>

#define MAX_DEVICES 64
#define BIT_WORDS(n) (((n) + 8 * sizeof(unsigned long) - 1) / (8 * sizeof(unsigned long)))
#define HAS(a,n) (((a)[(n) / (8 * sizeof(unsigned long))] >> ((n) % (8 * sizeof(unsigned long)))) & 1UL)
static struct pollfd devices[MAX_DEVICES];
static int touch[MAX_DEVICES], count, head, tail;
static uint64_t queue[128], generation, last_beat, probe_deadline;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static uint64_t millis(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000 + (uint64_t)ts.tv_nsec / 1000000;
}

static void close_all(void) {
    for (int i = 0; i < count; ++i) {
        if (touch[i]) ioctl(devices[i].fd, EVIOCGRAB, 0);
        close(devices[i].fd);
    }
    count = head = tail = 0;
    ++generation;
}
static void *watchdog(void *arg) {
    uint64_t session = *(uint64_t *)arg; free(arg);
    for (;;) {
        struct timespec delay = {0, 50000000}; nanosleep(&delay, NULL);
        pthread_mutex_lock(&lock);
        if (session != generation || !count) { pthread_mutex_unlock(&lock); return NULL; }
        uint64_t now = millis();
        int reason = now - last_beat > 5000 ? 5 : probe_deadline && now >= probe_deadline ? 6 : 0;
        if (reason) {
            close_all(); queue[tail++] = (now << 8) | (uint64_t)reason;
            pthread_mutex_unlock(&lock); return NULL;
        }
        pthread_mutex_unlock(&lock);
    }
}
static int fingers_down(void) {
    for (int i = 0; i < count; ++i) if (touch[i]) {
        unsigned long keys[BIT_WORDS(KEY_MAX + 1)] = {0};
        if (ioctl(devices[i].fd, EVIOCGKEY(sizeof(keys)), keys) < 0 || HAS(keys, BTN_TOUCH)) return 1;
    }
    return 0;
}
static jstring open_inputs(JNIEnv *env, int keys_only, int probe) {
    pthread_mutex_lock(&lock);
    close_all();
    char error[256] = "", path[256], name[128];
    int touches = 0, volume = 0, power = 0;
    DIR *dir = opendir("/dev/input");
    if (!dir) snprintf(error, sizeof(error), "无法读取输入设备：%s", strerror(errno));
    struct dirent *entry;
    while (dir && (entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5)) continue;
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        unsigned long keys[BIT_WORDS(KEY_MAX + 1)] = {0}, abs[BIT_WORDS(ABS_MAX + 1)] = {0};
        memset(name, 0, sizeof(name));
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys);
        ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs)), abs);
        int is_touch = !keys_only && (!strcmp(name, "sec_touchscreen") || !strcmp(name, "sec_touchscreen2"))
                && HAS(abs, ABS_MT_POSITION_X) && HAS(abs, ABS_MT_POSITION_Y) && HAS(keys, BTN_TOUCH);
        int has_volume = HAS(keys, KEY_VOLUMEDOWN), has_power = HAS(keys, KEY_POWER);
        if ((!is_touch && !has_volume && !has_power) || count >= MAX_DEVICES) { close(fd); continue; }
        int clock_id = CLOCK_MONOTONIC;
        if (ioctl(fd, EVIOCSCLOCKID, &clock_id) < 0) {
            snprintf(error, sizeof(error), "无法设置可靠的输入时钟：%s", strerror(errno)); close(fd); break;
        }
        devices[count] = (struct pollfd){ .fd = fd, .events = POLLIN };
        touch[count++] = is_touch;
        touches += is_touch; volume |= has_volume; power |= has_power;
    }
    if (dir) closedir(dir);
    if (!error[0] && ((!keys_only && touches != 2) || !volume || !power))
        snprintf(error, sizeof(error), "设备检查失败：触控屏=%d/2，音量下键=%d，电源键=%d", touches, volume, power);
    if (!error[0] && fingers_down()) snprintf(error, sizeof(error), "请先移开屏幕上的手指，再重试");
    if (!error[0]) for (int i = 0; i < count; ++i) if (touch[i]) {
        if (ioctl(devices[i].fd, EVIOCGRAB, 1) < 0) {
            snprintf(error, sizeof(error), "系统拒绝触控独占：%s (errno=%d)", strerror(errno), errno);
            break;
        }
    }
    if (!error[0] && fingers_down()) snprintf(error, sizeof(error), "检测到触控，已取消启动，请移开手指后重试");
    // Discard pre-session events, including the key used before starting the test.
    struct input_event events[32];
    if (!error[0]) for (int i = 0; i < count; ++i) while (read(devices[i].fd, events, sizeof(events)) > 0) {}
    if (error[0]) close_all();
    else {
        last_beat = millis(); probe_deadline = probe ? last_beat + 10000 : 0;
        uint64_t *session = malloc(sizeof(*session));
        pthread_t thread;
        if (session) *session = generation;
        int result = session ? pthread_create(&thread, NULL, watchdog, session) : ENOMEM;
        if (result) {
            free(session); close_all(); snprintf(error, sizeof(error), "无法启动输入恢复守护：%s", strerror(result));
        } else pthread_detach(thread);
    }
    pthread_mutex_unlock(&lock);
    return (*env)->NewStringUTF(env, error);
}
JNIEXPORT jstring JNICALL Java_local_ken_foldguard_NativeInput_start(JNIEnv *env, jclass unused, jboolean probe) {
    (void)unused;
    return open_inputs(env, 0, probe);
}
JNIEXPORT jstring JNICALL Java_local_ken_foldguard_NativeInput_watchKeys(JNIEnv *env, jclass unused) {
    (void)unused;
    return open_inputs(env, 1, 0);
}
static void enqueue(int event, uint64_t when) {
    if (tail - head < 128) queue[(tail++) % 128] = (when << 8) | (uint64_t)event;
    else { head = 0; tail = 1; queue[0] = (millis() << 8) | 5; }
}
JNIEXPORT jlong JNICALL Java_local_ken_foldguard_NativeInput_pollEvent(JNIEnv *env, jclass unused) {
    (void)env; (void)unused;
    pthread_mutex_lock(&lock);
    if (head == tail && count > 0) {
        int result = poll(devices, count, 50);
        if (result < 0 && errno != EINTR) enqueue(5, millis());
        for (int i = 0; i < count; ++i) {
            if (devices[i].revents & (POLLERR | POLLHUP | POLLNVAL)) enqueue(5, millis());
            if (!(devices[i].revents & POLLIN)) continue;
            struct input_event events[32]; ssize_t bytes;
            while ((bytes = read(devices[i].fd, events, sizeof(events))) > 0) {
                for (size_t j = 0; j < (size_t)bytes / sizeof(struct input_event); ++j) {
                    struct input_event *e = &events[j];
                    uint64_t when = (uint64_t)e->time.tv_sec * 1000 + (uint64_t)e->time.tv_usec / 1000;
                    if (e->type == EV_SYN && e->code == SYN_DROPPED) enqueue(5, when);
                    if (!touch[i] && e->type == EV_KEY) {
                        if (e->code == KEY_VOLUMEDOWN) enqueue(e->value == 1 ? 1 : e->value == 0 ? 2 : 3, when);
                        if (e->code == KEY_POWER && e->value == 1) enqueue(4, when);
                    }
                }
            }
            if (bytes < 0 && errno != EAGAIN && errno != EINTR) enqueue(5, millis());
        }
    }
    uint64_t result = head < tail ? queue[(head++) % 128] : 0;
    pthread_mutex_unlock(&lock);
    return result;
}
JNIEXPORT void JNICALL Java_local_ken_foldguard_NativeInput_heartbeat(JNIEnv *env, jclass unused) {
    (void)env; (void)unused;
    pthread_mutex_lock(&lock); last_beat = millis(); pthread_mutex_unlock(&lock);
}
JNIEXPORT jboolean JNICALL Java_local_ken_foldguard_NativeInput_fingersDown(JNIEnv *env, jclass unused) {
    (void)env; (void)unused;
    pthread_mutex_lock(&lock); int result = fingers_down(); pthread_mutex_unlock(&lock);
    return result ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_local_ken_foldguard_NativeInput_stop(JNIEnv *env, jclass unused) {
    (void)env; (void)unused;
    pthread_mutex_lock(&lock); close_all(); pthread_mutex_unlock(&lock);
}
