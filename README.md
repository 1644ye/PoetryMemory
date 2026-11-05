# 背诗 · PoetryMemory

基于静读 Android 工程的离线背诗软件。新安装默认空库，支持原有 `ebook-library` JSON、诗集模板、GitHub `catalog.json` 和小说阅读。

## 安装与构建

Android 8.0（API 26）及以上。源码版本 **1.5.0 / versionCode 14**。

使用 JDK 21、Android SDK 36，在项目根目录设置 `local.properties`：

```properties
sdk.dir=你的Android SDK路径
```

```powershell
./gradlew.bat :core:test :app:lintDebug :app:assembleRelease
```

安装包位于 `app/build/outputs/apk/release/app-release.apk`。Linux/macOS 可运行 `bash gradlew`。当前 Release 为非调试构建，沿用本机 Android 开发签名。自行分发后续版本时必须保存并沿用同一签名；签名文件不在仓库中。

保留 `org.quietreader` 包名及原 SQLite 数据库，可在签名一致时覆盖升级静读 1.4.1；升级不会清空书籍和记录。两个应用不能以同一包名同时安装。

## 开始使用

1. 首页 → 首页设置 → 数据 → 下载模板与示例，保存 JSON；或使用已有诗库文件。
2. 数据 → 导入 JSON，查看预览后确认。
3. 首页搜索标题、作者或原文，点诗文进入阅读。
4. 阅读页 → 背诵，选择整篇、逐句或接续；首页也可抽背当前筛选结果。
5. 需要离开时保存并暂停，重新打开“继续学习”恢复会话。

可直接使用 [诗集模板](core/src/main/resources/protocol/poetry-template.json)、[诗歌示例](core/src/main/resources/protocol/poetry-example.json) 和 [catalog 示例](docs/templates/catalog.json)。正文协议、模板与静读保持一致；原文、拼音、逐句译文和注释，以及创作背景、全篇译文与赏析均来自导入内容或用户编辑。

## 功能入口

| 功能 | 入口及行为 |
| --- | --- |
| 搜索、收藏、诗集、作者、标签 | 首页；诗集只保存成员关系，删除诗集保留诗文 |
| 筛选排序 | 首页设置 → 筛选排序；AND/OR 条件组、字数范围、独立诗文与分组排序及升降序；查询变化重置分页 |
| 外观 | 首页设置 → 外观布局；主题、列表/网格、列数、密度和首页显示管理 |
| 阅读 | 连续/逐句；自动、一句一行、两句一行、段落；按实际字体宽度适配，长文按需构建 |
| 阅读设置 | 辅助、正文、交互三层；保留原单书设置，支持单篇覆盖及恢复继承 |
| 手势与工具栏 | 阅读页交互区域/正文空白区域的轻点、双击、长按、横滑；原文可选择复制；工具栏支持显隐、顺序、字号和自动隐藏 |
| 安全恢复 | 独立“按住 5 秒恢复交互”触点，保留按压进度和可访问点击操作 |
| 编辑 | 元数据、逐句原文/拼音/译注、篇级资料；上移/下移、复制、拆分、合并和分节 |
| 草稿与冲突 | 独立持久草稿；保存时事务比较正文指纹；过期草稿不能覆盖新内容；来源版本可查看并选择采用 |
| 背诵 | 整篇、逐句、接续；七类提示、查看答案、备注和暂停恢复 |
| 抽背 | 整篇、随机句、接续、题目提示、隐藏题目；等权、薄弱优先、手动权重；题数、轮数及三种重复策略 |
| 学习历史 | 0–3 分用户自评；跳过独立计数；均分仅以已评分题计算；可修改历史评价 |
| 原书架与小说 | 首页设置 → 原书架与小说；保留原有小说、目录、书签、批注及书籍管理 |
| 备份 | 数据 → 完整备份/恢复；包含正文、关系、覆盖、草稿、阅读设置、会话和历史；恢复前保留保护备份 |

普通导出输出当前有效正文，学习私有数据只进入备份。原书架的“所选备份”按书籍范围导出，已过滤其他书籍的诗文草稿、覆盖、诗集成员和题目快照。

## GitHub 诗库与软件更新

数据 → GitHub 账号、诗库与阅读进度同步，使用自己的账号和仓库。书源与进度使用独立授权，书源只读；进度仓库需独立且私有。参见 [登录配置](docs/GitHub登录配置.md) 和 [JSON 协议](docs/JSON协议.md)。

数据 → 诗库更新策略，支持手动检查、更新已下载内容，以及每日自动检查、可选自动下载和仅 Wi-Fi。自动任务受 Android 后台调度影响；不会自动下载整个仓库。移除来源或上游诗文不清除已下载的离线诗文。

编辑内容作为本地覆盖保存。来源更新保留覆盖，阅读页“来源版本”可查看来源正文或明确采用来源新版。学习题目使用开始时的快照；本地覆盖所在书籍暂停自动应用跨设备阅读位置。学习历史、诗集关系和草稿使用本地备份迁移。

软件更新独立读取 [PoetryMemory Releases](https://github.com/1644ye/PoetryMemory/releases)。首页设置 → 更新，可自动检查、可选自动下载，安装前验证包名、递增版本与签名，最后由系统确认安装。发布维护者需上传正式 `v主版本.次版本.修订号` Release 及单个 APK，之后更新检查才能发现新版。

## 验证

本次本地验证结果与环境见 [验收记录](docs/验收记录.md)。原方案保存在 [requirements.txt](requirements.txt)，文档中的命令字样仅为原始需求材料。

测试命令：

```powershell
./gradlew.bat :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w org.quietreader.test/android.test.InstrumentationTestRunner
```
