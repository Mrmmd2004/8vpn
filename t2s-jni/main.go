// Package main builds xjasonlyu/tun2socks as an Android shared library (libt2s-jni.so).
//
// Kotlin talks to it through two JNI functions (see jni_shim.c) that take plain ints only:
//
//	start(tunFd, socksPort, mtu) -> 0 on success
//	stop()
//
// The Go runtime lives inside the app process. The TUN fd handed to start() is a dup owned by
// this library: tun2socks closes it on stop().
package main

import "C"

import (
	"fmt"
	"io"
	"sync"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/engine"
	t2slog "github.com/xjasonlyu/tun2socks/v2/log"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

var (
	mu      sync.Mutex
	running bool
)

// installNonFatalLogger keeps tun2socks from calling os.Exit(1) (which would kill the whole app)
// if engine.Stop() ever reports an error: a Fatal record becomes a recoverable panic instead.
// The level is clamped to Fatal so the record is not filtered out before the hook runs.
func installNonFatalLogger() {
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		zapcore.AddSync(io.Discard),
		zapcore.FatalLevel,
	)
	t2slog.SetLogger(zap.New(core, zap.OnFatal(zapcore.WriteThenPanic)))
}

//export t2s_start
func t2s_start(fd C.int, port C.int, mtu C.int, bufKb C.int) (rc C.int) {
	mu.Lock()
	defer mu.Unlock()

	if running {
		return -1
	}
	if fd < 0 || port < 1 || port > 65535 {
		return -2
	}
	m := int(mtu)
	if m <= 0 || m > 65535 {
		m = 1500
	}

	kb := int(bufKb)
	if kb < 16 || kb > 4096 {
		kb = 256
	}

	defer func() {
		if r := recover(); r != nil {
			rc = -3
		}
	}()

	engine.Insert(&engine.Key{
		Proxy:      fmt.Sprintf("socks5://127.0.0.1:%d", int(port)),
		Device:     fmt.Sprintf("fd://%d", int(fd)),
		MTU:        m,
		LogLevel:   "silent",
		UDPTimeout: 30 * time.Second,
		// TCP buffers from Settings (default 256 KB, same as the old FCAE build), no auto-tuning.
		TCPSendBufferSize:        fmt.Sprintf("%dk", kb),
		TCPReceiveBufferSize:     fmt.Sprintf("%dk", kb),
		TCPModerateReceiveBuffer: false,
	})
	engine.Start()
	installNonFatalLogger()
	running = true
	return 0
}

//export t2s_stop
func t2s_stop() {
	mu.Lock()
	defer mu.Unlock()
	if !running {
		return
	}
	running = false
	defer func() { _ = recover() }()
	engine.Stop()
}

func main() {}
