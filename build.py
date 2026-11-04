# @author bomo
# 用途：构建功能补全开发版；无参数，输出 build/color-completion-dev.apk。
# 边界：依赖本机 Android SDK 与 JDK；失败抛出异常，密钥仅用于开发测试。
from pathlib import Path
import os, subprocess, zipfile
os.chdir(Path(__file__).parent)
bt = Path('D:/Software/SDK/build-tools/35.0.0')
android = 'D:/Software/SDK/platforms/android-35/android.jar'
for path in ('build/obj', 'build/dex', 'build/gen'): Path(path).mkdir(parents=True, exist_ok=True)
def run(*args):
    # 执行命令并检查退出状态；参数为命令及参数，失败抛出异常。
    subprocess.run([str(x) for x in args], check=True)
run(bt/'aapt2.exe', 'compile', '--dir', 'res', '-o', 'build/res.zip')
run(bt/'aapt2.exe', 'link', '-I', android, '--manifest', 'AndroidManifest.xml', '-R', 'build/res.zip', '--auto-add-overlay', '--java', 'build/gen', '-o', 'build/base.apk')
sources = list(Path('src').rglob('*.java')) + list(Path('build/gen').rglob('*.java'))
run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', android + os.pathsep + 'libs/libxposed-api-102.0.0.jar' + os.pathsep + 'libs/libxposed-service-102.0.0.jar' + os.pathsep + 'libs/libxposed-interface-102.0.0.jar', '-d', 'build/obj', *sources)
classes = list(Path('build/obj/com').rglob('*.class'))
run(bt/'d8.bat', '--min-api', '26', '--lib', android, '--output', 'build/dex', *classes, 'libs/libxposed-service-102.0.0.jar', 'libs/libxposed-interface-102.0.0.jar')
with zipfile.ZipFile('build/base.apk') as source, zipfile.ZipFile('build/packed.apk', 'w', zipfile.ZIP_DEFLATED) as output:
    for entry in source.infolist(): output.writestr(entry, source.read(entry.filename))
    output.write('build/dex/classes.dex', 'classes.dex')
    output.write('modern/java_init.list', 'META-INF/xposed/java_init.list')
    output.write('modern/module.prop', 'META-INF/xposed/module.prop')
    output.write('modern/scope.list', 'META-INF/xposed/scope.list')
if not Path('build/dev.keystore').exists():
    run('keytool', '-genkeypair', '-keystore', 'build/dev.keystore', '-storepass', 'android', '-keypass', 'android', '-alias', 'dev', '-dname', 'CN=Development', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650')
run(bt/'zipalign.exe', '-f', '4', 'build/packed.apk', 'build/aligned.apk')
run(bt/'apksigner.bat', 'sign', '--ks', 'build/dev.keystore', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', 'build/color-completion-dev.apk', 'build/aligned.apk')
run(bt/'apksigner.bat', 'verify', 'build/color-completion-dev.apk')
print('BUILD_OK')


