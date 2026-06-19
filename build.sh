#!/bin/bash
# 金铲铲之战自动助手 - 一键编译脚本
# 使用前请确保已安装:
#   - JDK 11+ (推荐 JDK 17)
#   - Android SDK (API 33)
#   - 设置 ANDROID_HOME 环境变量

set -e

echo "⚔️ 金铲铲自动 - 编译中..."
echo ""

# 检查环境
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    # 尝试默认路径
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    else
        echo "❌ 未找到 Android SDK，请设置 ANDROID_HOME 环境变量"
        exit 1
    fi
fi

echo "📱 Android SDK: $ANDROID_HOME"

cd "$(dirname "$0")"

# 下载 gradle wrapper jar (如果没有)
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "📦 下载 Gradle Wrapper..."
    mkdir -p gradle/wrapper
    curl -sL "https://services.gradle.org/distributions/gradle-8.2-bin.zip" -o /tmp/gradle.zip
    unzip -qo /tmp/gradle.zip -d /tmp/gradle-dist
    cp /tmp/gradle-dist/gradle-8.2/lib/gradle-wrapper-main-8.2.jar "$WRAPPER_JAR" 2>/dev/null || true
    rm -rf /tmp/gradle.zip /tmp/gradle-dist
fi

# 编译
echo "🔨 开始编译..."
./gradlew assembleDebug --no-daemon

# 结果
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "✅ 编译成功！"
    echo "📦 APK: $APK_PATH ($SIZE)"
    echo ""
    echo "安装到手机:"
    echo "  adb install -r $APK_PATH"
else
    echo "❌ 编译失败"
    exit 1
fi
