# 模块快速导航

> 按「我想改什么」快速定位到对应文件。所有路径基于 `app/src/main/java/io/legado/app/`。

---

## 一、按功能场景定位

### 📖 阅读相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 阅读页主界面（翻页、手势、底部栏） | `ui/book/read/ReadBookActivity.kt`、`ui/book/read/page/ReadView.kt`、`ui/book/read/page/PageView.kt` |
| 阅读内容渲染（文字排版、绘制） | `ui/book/read/page/ContentTextView.kt`、`ui/book/read/page/provider/` |
| 阅读菜单（亮度、字号、目录、阅读页底部栏等） | `ui/book/read/ReadMenu.kt`、`ui/book/read/config/` |
| 翻页动画/滑动方式 | `ui/book/read/page/delegate/` |
| 阅读状态管理（当前书籍、章节、进度） | `model/ReadBook.kt` |
| 阅读配置（字号、行距、背景等） | `help/config/ReadBookConfig.kt`、`help/config/ReadTipConfig.kt` |
| 阅读页自定义控件（进度条、信息栏、热力图） | `ui/widget/ReadBarChartView.kt`、`ui/widget/ReaderInfoBarView.kt`、`ui/widget/ReadHeatmapView.kt` |
| 漫画阅读 | `ui/book/manga/ReadMangaActivity.kt`、`model/ReadManga.kt`、`ui/widget/image/PhotoView.kt` |

### 🔊 朗读/音频相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 音频播放界面 | `ui/book/audio/AudioPlayActivity.kt`、`ui/book/audio/AudioPlayViewModel.kt` |
| TTS 本地朗读 | `service/TTSReadAloudService.kt`、`help/TTS.kt` |
| 在线朗读 | `service/HttpReadAloudService.kt` |
| 朗读设置弹窗 | `ui/book/read/config/ReadAloudDialog.kt`、`ui/book/read/config/SpeakEngineDialog.kt` |
| 朗读状态管理 | `model/ReadAloud.kt` |

### 🔍 搜索相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 搜索界面 | `ui/book/search/SearchActivity.kt`、`ui/book/search/SearchViewModel.kt` |
| 搜索结果适配器 | `ui/book/search/SearchAdapter.kt`、`ui/book/search/BookAdapter.kt` |
| 搜索范围/书源筛选 | `ui/book/search/SearchScope.kt`、`ui/book/search/SearchScopeDialog.kt` |
| 搜索网络请求 | `model/webBook/SearchModel.kt` |
| 搜索历史 | `data/dao/SearchKeywordDao.kt`、`data/entities/SearchKeyword.kt` |
| 书籍内容全文搜索 | `ui/book/searchContent/SearchContentActivity.kt` |

### 📚 书架相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 书架主界面（Compose 版） | `ui/main/bookshelf/compose/BookshelfScreen.kt`（主 Composable）、`ui/main/bookshelf/compose/BookshelfComposeFragment.kt`（Fragment 宿主） |
| 书架列表项（封面+书名+作者+章节） | `ui/main/bookshelf/compose/BookListItem.kt` |
| 书架网格项（封面+书名） | `ui/main/bookshelf/compose/BookGridItem.kt` |
| 书架主界面（旧版 Style1） | `ui/main/bookshelf/style1/`（已由 Compose 版替换） |
| 书架主界面（旧版 Style2） | `ui/main/bookshelf/style2/` |
| 书架 ViewModel | `ui/main/bookshelf/BookshelfViewModel.kt` |
| 书架管理（批量操作） | `ui/book/manage/BookshelfManageActivity.kt` |
| 书籍分组 | `ui/book/group/`、`data/entities/BookGroup.kt`、`data/dao/BookGroupDao.kt` |
| 书籍详情页（Compose 版） | `ui/book/info/compose/BookDetailScreen.kt`（主 Composable）、`ui/book/info/compose/BookInfoComposeActivity.kt`（Activity 宿主） |
| 书籍详情页 Hero Header（模糊背景+封面+标题） | `ui/book/info/compose/HeroHeader.kt` |
| 书籍详情页 Chip 信息行（字数/进度/更新时间） | `ui/book/info/compose/InfoChipRow.kt` |
| 书籍详情页简介卡片（可折叠） | `ui/book/info/compose/IntroCard.kt` |
| 书籍详情页目录卡片 | `ui/book/info/compose/ChapterCard.kt` |
| 书籍详情页更多菜单（复用 book_info.xml） | `ui/book/info/compose/BookDetailMenu.kt` |
| 书籍详情页（旧版） | `ui/book/info/BookInfoActivity.kt`、`ui/book/info/BookInfoViewModel.kt` |
| 书籍详情信息编辑 | `ui/book/info/edit/` |
| 封面换源 | `ui/book/changecover/ChangeCoverDialog.kt`、`model/BookCover.kt` |
| 书籍换源 | `ui/book/changesource/ChangeBookSourceDialog.kt`、`ui/book/changesource/ChangeChapterSourceDialog.kt` |
| 目录/书签 | `ui/book/toc/TocActivity.kt`、`ui/book/toc/TocViewModel.kt`、`ui/book/toc/ChapterListFragment.kt`、`ui/book/toc/ChapterListAdapter.kt`、`ui/book/toc/BookmarkFragment.kt`、`ui/book/toc/BookmarkAdapter.kt`、`ui/book/toc/TocActivityResult.kt` |
| TXT 目录规则 | `ui/book/toc/rule/TxtTocRuleActivity.kt`、`ui/book/toc/rule/TxtTocRuleDialog.kt`、`ui/book/toc/rule/TxtTocRuleEditDialog.kt` |
| 书签管理 | `ui/book/bookmark/AllBookmarkActivity.kt`、`ui/book/bookmark/BookmarkDialog.kt` |
| 缓存管理 | `ui/book/cache/CacheActivity.kt`、`model/CacheBook.kt`、`help/CacheManager.kt` |

### 🎨 Compose 共享组件

| 组件 | 文件路径 | 说明 |
|------|----------|------|
| M3 Theme 桥接 | `ui/common/compose/LegadoTheme.kt` | 从 ThemeStore 读取颜色映射到 Material 3 ColorScheme，支持 Light/Dark/E-Ink 三分支 |
| 封面图片 | `ui/common/compose/BookCoverImage.kt` | GlideImage 加载封面 URL，5:7 比例，8dp 圆角，失败时显示默认封面 |
| 卡片容器 | `ui/common/compose/SectionCard.kt` | Card + 12dp 内边距，使用 `legadoCardBackgroundColor()` 背景色 |
| 信息 Chip | `ui/common/compose/InfoChip.kt` | SuggestionChip，支持 outlined（surfaceVariant）和 filled（primaryContainer） |
| 可折叠文本 | `ui/common/compose/CollapsibleText.kt` | 默认 3 行，点击展开/折叠，E-Ink 模式禁用动画 |
| 空状态视图 | `ui/common/compose/EmptyStateView.kt` | 居中图标 + 提示文字 |

### 🌐 发现/书源相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 发现页（书源推荐） | `ui/main/explore/ExploreFragment.kt`、`ui/book/explore/` |
| 书源管理 | `ui/book/source/manage/BookSourceActivity.kt` |
| 书源编辑 | `ui/book/source/edit/BookSourceEditActivity.kt` |
| 书源调试 | `ui/book/source/debug/BookSourceDebugActivity.kt` |
| 书源规则解析引擎 | `model/analyzeRule/AnalyzeRule.kt`、`model/analyzeRule/AnalyzeUrl.kt` |
| 书源规则语法分析 | `model/analyzeRule/RuleAnalyzer.kt` |
| 网络书源请求 | `model/webBook/WebBook.kt`、`model/webBook/BookList.kt`、`model/webBook/BookInfo.kt`、`model/webBook/BookChapterList.kt`、`model/webBook/BookContent.kt` |
| 书源检测 | `model/CheckSource.kt`、`service/CheckSourceService.kt` |
| 书源实体/数据 | `data/entities/BookSource.kt`、`data/entities/rule/` |
| 字体反爬 | `model/analyzeRule/QueryTTF.java` |

### 📰 RSS 订阅相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| RSS 主界面 | `ui/main/rss/RssFragment.kt` |
| RSS 源管理 | `ui/rss/source/manage/RssSourceActivity.kt` |
| RSS 源编辑 | `ui/rss/source/edit/RssSourceEditActivity.kt` |
| RSS 文章列表 | `ui/rss/article/` |
| RSS 文章阅读 | `ui/rss/read/` |
| RSS 收藏 | `ui/rss/favorites/` |
| RSS 订阅管理 | `ui/rss/subscription/` |
| RSS 解析逻辑 | `model/rss/Rss.kt`、`model/rss/RssParserDefault.kt`、`model/rss/RssParserByRule.kt` |

### ⚙️ 设置/配置相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 设置主界面 | `ui/config/ConfigActivity.kt`、`ui/config/ConfigViewModel.kt` |
| 主题/颜色设置 | `ui/config/ThemeConfigFragment.kt`、`help/config/ThemeConfig.kt`、`lib/theme/` |
| 备份恢复设置 | `ui/config/BackupConfigFragment.kt`、`help/storage/Backup.kt`、`help/storage/Restore.kt` |
| 封面设置 | `ui/config/CoverConfigFragment.kt` |
| 其他设置 | `ui/config/OtherConfigFragment.kt`、`help/config/AppConfig.kt` |
| 欢迎页设置 | `ui/config/WelcomeConfigFragment.kt` |
| 直链上传配置 | `ui/config/DirectLinkUploadConfig.kt`、`help/DirectLinkUpload.kt` |
| 偏好设置 UI 组件 | `lib/prefs/`（ColorPreference、SwitchPreference 等） |
| 全局常量/偏好键名 | `constant/PreferKey.kt`、`constant/EventBus.kt`、`constant/IntentAction.kt` |

### 🔄 替换净化相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 替换规则管理界面 | `ui/replace/ReplaceRuleActivity.kt` |
| 替换规则编辑 | `ui/replace/edit/` |
| 替换规则分组 | `ui/replace/GroupManageDialog.kt` |
| 替换规则解析执行 | `help/ReplaceAnalyzer.kt` |
| 替换规则实体 | `data/entities/ReplaceRule.kt`、`data/dao/ReplaceRuleDao.kt` |

### 📥 导入/关联相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 本地书籍导入 | `ui/book/import/ImportBookActivity.kt`、`model/localBook/LocalBook.kt` |
| TXT 解析 | `model/localBook/TextFile.kt` |
| EPUB 解析 | `model/localBook/EpubFile.kt` |
| PDF 解析 | `model/localBook/PdfFile.kt` |
| UMD 解析 | `model/localBook/UmdFile.kt` |
| MOBI 解析 | `lib/mobi/MobiBook.kt`、`lib/mobi/MobiReader.kt` |
| 书源在线导入 | `ui/association/OnLineImportActivity.kt`、`ui/association/ImportBookSourceDialog.kt` |
| 替换规则导入 | `ui/association/ImportReplaceRuleDialog.kt` |
| RSS 源导入 | `ui/association/ImportRssSourceDialog.kt` |
| 主题导入 | `ui/association/ImportThemeDialog.kt` |
| 文件关联打开 | `ui/association/FileAssociationActivity.kt` |
| URL 确认 | `ui/association/OpenUrlConfirmActivity.kt` |
| 验证码处理 | `ui/association/VerificationCodeActivity.kt` |

### 🔐 登录/认证相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 书源登录界面 | `ui/login/SourceLoginActivity.kt`、`ui/login/SourceLoginDialog.kt` |
| WebView 登录 | `ui/login/WebViewLoginFragment.kt` |
| Cookie 管理 | `help/http/CookieStore.kt`、`help/http/CookieManager.kt`、`data/entities/Cookie.kt` |

### 🌍 Web 服务相关

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| Web 服务开关/设置 | `service/WebService.kt`、`service/WebTileService.kt` |
| HTTP API 接口 | `web/HttpServer.kt`、`api/controller/` |
| WebSocket 实时通信 | `web/WebSocketServer.kt`、`web/socket/` |
| Web 前端（Vue 3） | `modules/web/src/`（独立模块） |
| Content Provider API | `api/ReaderProvider.kt` |

### � 我的

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| "我的"页主界面（设置项列表、点击跳转） | `ui/main/my/MyFragment.kt` |
| "我的"页设置项定义（内容源管理、我的数据、应用设置等） | `res/xml/pref_main.xml` |
| "我的"页布局（TitleBar + PreferenceFragment 容器） | `res/layout/fragment_my_config.xml` |
| "我的"页菜单（帮助按钮） | `res/menu/main_my.xml` |
| Web 服务地址长按操作（复制/浏览器打开） | `ui/main/my/MyFragment.kt` 中 `webService` 的 `onLongClick` |
| 偏好设置 UI 组件（Preference、SwitchPreference 等） | `lib/prefs/Preference.kt`、`lib/prefs/SwitchPreference.kt`、`lib/prefs/NameListPreference.kt`、`lib/prefs/PreferenceCategory.kt` |
| 偏好设置卡片样式（圆角、背景、间距） | `lib/prefs/CardPositionHelper.kt` |

### �📖 关于/其他页面

| 想改的功能 | 关键文件路径 |
|-----------|-------------|
| 关于页面 | `ui/about/AboutActivity.kt`、`ui/about/AboutFragment.kt` |
| 更新弹窗 | `ui/about/UpdateDialog.kt`、`help/update/AppUpdate.kt` |
| 阅读记录 | `ui/about/ReadRecordActivity.kt`、`data/dao/DailyReadRecordDao.kt` |
| 崩溃日志 | `ui/about/CrashLogsDialog.kt`、`help/CrashHandler.kt` |
| 欢迎/启动页 | `ui/welcome/WelcomeActivity.kt` |
| 二维码扫描 | `ui/qrcode/QrCodeActivity.kt`、`ui/qrcode/QrCodeFragment.kt` |
| 词典查询 | `ui/dict/DictDialog.kt`、`ui/dict/rule/` |
| 浮窗/PopupWindow | `ui/widget/ThemedPopupWindow.kt`、`ui/widget/PopupAction.kt`、`ui/book/read/TextActionMenu.kt`、`utils/PopupThemeApplier.kt` |
| 字体选择 | `ui/font/FontSelectDialog.kt` |
| 文件管理 | `ui/file/FileManageActivity.kt`、`ui/file/FilePickerDialog.kt` |
| 内置浏览器 | `ui/browser/WebViewActivity.kt` |

---

## 二、按目录结构定位

```
app/src/main/java/io/legado/app/
├── App.kt                          ← 应用入口，全局初始化
├── api/                            ← 对外 API（Content Provider + Web 控制器）
│   ├── ReaderProvider.kt           ← Content Provider，供外部 App 读取书籍数据
│   ├── ReturnData.kt               ← API 返回数据格式
│   ├── ShortCuts.kt                ← 快捷方式
│   └── controller/                 ← Web API 控制器
│       ├── BookController.kt       ← 书籍相关 API
│       ├── BookSourceController.kt ← 书源相关 API
│       ├── ReplaceRuleController.kt← 替换规则 API
│       └── RssSourceController.kt  ← RSS 源 API
├── base/                           ← 基类（Activity/Fragment/ViewModel/Adapter/Service）
├── constant/                       ← 全局常量（事件、意图、偏好键名、类型枚举）
├── data/                           ← 数据层（Room 数据库）
│   ├── AppDatabase.kt              ← 数据库定义，全局实例 appDb
│   ├── DatabaseMigrations.kt       ← 数据库迁移（v1→v76）
│   ├── dao/                        ← 22 个 DAO 接口
│   └── entities/                   ← 30+ 实体类
│       └── rule/                   ← 书源规则实体
├── exception/                      ← 自定义异常
├── help/                           ← 业务辅助工具
│   ├── book/                       ← 书籍内容处理
│   ├── config/                     ← 配置管理（App/ReadBook/Theme/Source）
│   ├── coroutine/                  ← 协程封装
│   ├── crypto/                     ← 加解密
│   ├── exoplayer/                  ← 音频播放器
│   ├── glide/                      ← 图片加载
│   ├── http/                       ← HTTP 客户端（OkHttp + Cronet）
│   ├── rhino/                      ← JS 引擎封装
│   ├── source/                     ← 书源扩展函数
│   ├── storage/                    ← 备份恢复
│   └── update/                     ← 应用更新
├── lib/                            ← 第三方库封装
│   ├── aliyun/                     ← 阿里云
│   ├── cronet/                     ← Cronet 网络库
│   ├── dialogs/                    ← 对话框
│   ├── icu4j/                      ← 编码识别
│   ├── mobi/                       ← MOBI 解析
│   ├── permission/                 ← 权限请求
│   ├── prefs/                      ← 偏好设置组件
│   ├── theme/                      ← 主题引擎
│   └── webdav/                     ← WebDAV 网络存储
├── model/                          ← 业务逻辑核心
│   ├── analyzeRule/                ← 书源规则解析引擎
│   ├── localBook/                  ← 本地书籍解析（TXT/EPUB/PDF/UMD）
│   ├── remote/                     ← 远程书籍管理（WebDAV）
│   ├── rss/                        ← RSS 解析
│   ├── webBook/                    ← 网络书源请求
│   ├── ReadBook.kt                 ← 阅读状态管理（核心）
│   ├── ReadAloud.kt                ← 朗读状态管理
│   ├── AudioPlay.kt                ← 音频播放状态管理
│   └── ...                         ← 其他业务状态
├── receiver/                       ← 广播接收器
├── service/                        ← 后台服务
│   ├── AudioPlayService.kt         ← 音频播放服务
│   ├── TTSReadAloudService.kt      ← TTS 朗读服务
│   ├── HttpReadAloudService.kt     ← 在线朗读服务
│   ├── CacheBookService.kt         ← 缓存服务
│   ├── DownloadService.kt          ← 下载服务
│   ├── CheckSourceService.kt       ← 书源检测服务
│   ├── ExportBookService.kt        ← 导出服务
│   └── WebService.kt               ← Web 服务
├── ui/                             ← 界面层
│   ├── about/                      ← 关于页面
│   ├── association/                ← 关联导入
│   ├── common/                     ← ⭐ Compose 共享组件
│   │   └── compose/
│   │       ├── LegadoTheme.kt      ← M3 Theme 桥接（ThemeStore → ColorScheme）
│   │       ├── BookCoverImage.kt   ← GlideImage 封面加载
│   │       ├── SectionCard.kt      ← 通用卡片容器
│   │       ├── InfoChip.kt         ← 信息 Chip（outlined/filled）
│   │       ├── CollapsibleText.kt  ← 可折叠文本
│   │       └── EmptyStateView.kt   ← 空状态视图
│   ├── book/                       ← 书籍相关
│   │   ├── audio/                  ← 音频播放
│   │   ├── bookmark/               ← 书签管理
│   │   ├── cache/                  ← 缓存管理
│   │   ├── changecover/            ← 封面换源
│   │   ├── changesource/           ← 书籍换源
│   │   ├── explore/                ← 书源发现
│   │   ├── group/                  ← 分组管理
│   │   ├── import/                 ← 本地导入
│   │   ├── info/                   ← 书籍详情
│   │   │   └── compose/            ← ⭐ Compose 版详情页
│   │   │       ├── BookDetailScreen.kt  ← 主 Composable（Hero Header + 卡片布局）
│   │   │       ├── BookInfoComposeActivity.kt ← Activity 宿主（ViewModel + 回调）
│   │   │       ├── HeroHeader.kt  ← 模糊封面背景 + 大封面 + 书名
│   │   │       ├── InfoChipRow.kt ← 字数/进度/更新时间 Chip 行
│   │   │       ├── IntroCard.kt   ← 可折叠简介卡片
│   │   │       ├── ChapterCard.kt ← 目录卡片
│   │   │       └── BookDetailMenu.kt ← 更多菜单（复用 book_info.xml）
│   │   ├── manage/                 ← 书架管理
│   │   ├── manga/                  ← 漫画阅读
│   │   ├── read/                   ← ⭐ 阅读界面（核心）
│   │   │   ├── config/             ← 阅读设置弹窗
│   │   │   └── page/               ← 翻页/绘制引擎
│   │   ├── search/                 ← 搜索
│   │   ├── searchContent/          ← 内容搜索
│   │   ├── source/                 ← 书源管理/编辑/调试
│   │   └── toc/                    ← 目录
│   │       └── rule/               ← TXT 目录规则
│   ├── browser/                    ← 内置浏览器
│   ├── config/                     ← 设置页面
│   ├── dict/                       ← 词典
│   ├── file/                       ← 文件管理
│   ├── font/                       ← 字体选择
│   ├── login/                      ← 书源登录
│   ├── main/                       ← 主界面
│   │   ├── bookshelf/              ← 书架标签
│   │   │   ├── compose/            ← ⭐ Compose 版书架
│   │   │   │   ├── BookshelfScreen.kt       ← 主 Composable（TopAppBar + Tab + List/Grid）
│   │   │   │   ├── BookshelfComposeFragment.kt ← Fragment 宿主（ComposeView + 数据桥接）
│   │   │   │   ├── BookListItem.kt          ← 列表项（封面72×102 + 书名 + 作者 + 章节）
│   │   │   │   └── BookGridItem.kt          ← 网格项（封面 + 书名）
│   │   │   ├── style1/             ← 旧版 Style1（已由 Compose 版替换）
│   │   │   ├── style2/             ← 旧版 Style2
│   │   │   ├── BaseBookshelfFragment.kt ← 书架基类（菜单/导入导出/分组管理）
│   │   │   └── BookshelfViewModel.kt ← 书架 ViewModel
│   │   ├── explore/                ← 发现标签
│   │   ├── rss/                    ← RSS 标签
│   │   └── my/                     ← 我的标签
│   ├── qrcode/                     ← 二维码扫描
│   ├── replace/                    ← 替换规则
│   ├── rss/                        ← RSS 订阅
│   ├── welcome/                    ← 欢迎页
│   └── widget/                     ← 自定义 UI 组件库
│       ├── anima/                  ← 动画控件
│       ├── checkbox/               ← 复选框
│       ├── code/                   ← 代码编辑器
│       ├── dialog/                 ← 通用对话框
│       ├── dynamiclayout/          ← 状态切换布局
│       ├── image/                  ← 图片控件
│       ├── keyboard/               ← 键盘辅助
│       ├── number/                 ← 数字选择器
│       ├── recycler/               ← RecyclerView 辅助
│       ├── seekbar/                ← 滑块控件
│       ├── text/                   ← 文本控件
│       ├── ThemedPopupWindow.kt    ← 浮窗基类
│       ├── PopupAction.kt          ← 通用文本浮窗
│       ├── RoundedSpinner.kt       ← 圆角 Spinner
│       ├── TitleBar.kt             ← 标题栏
│       └── ...                     ← SelectActionBar 等
├── utils/                          ← 80+ 扩展函数文件
└── web/                            ← 内置 Web 服务器
    ├── HttpServer.kt               ← HTTP REST API
    ├── WebSocketServer.kt          ← WebSocket 实时通信
    ├── socket/                     ← WebSocket 处理器
    └── utils/                      ← 静态资源服务
```

---

## 三、常见修改场景速查

| 场景 | 需要改的文件 |
|------|-------------|
| 修改阅读页翻页动画 | `ui/book/read/page/delegate/` |
| 修改阅读页字体/行距/背景 | `help/config/ReadBookConfig.kt`、`ui/book/read/config/BgTextConfigDialog.kt` |
| 修改阅读页底部菜单 | `ui/book/read/ReadMenu.kt`、`res/layout/view_read_menu.xml` |
| 修改书架显示样式（Compose 版） | `ui/main/bookshelf/compose/BookshelfScreen.kt`（列表/网格切换、Tab 分组）、`BookListItem.kt`/`BookGridItem.kt`（卡片布局） |
| 修改书架显示样式（旧版） | `ui/main/bookshelf/style1/` 或 `style2/` |
| 修改书籍详情页布局（Compose 版） | `ui/book/info/compose/BookDetailScreen.kt`、`HeroHeader.kt`、`IntroCard.kt`、`ChapterCard.kt` |
| 修改书籍详情页更多菜单 | `ui/book/info/compose/BookDetailMenu.kt`（从 `res/menu/book_info.xml` 加载） |
| 修改搜索结果排序/过滤 | `ui/book/search/SearchViewModel.kt`、`model/webBook/SearchModel.kt` |
| 修改书源规则解析方式 | `model/analyzeRule/` |
| 修改网络请求（超时/代理/UA） | `help/http/HttpHelper.kt`、`help/http/OkHttpUtils.kt` |
| 修改主题颜色 | `lib/theme/ThemeStore.kt`、`help/config/ThemeConfig.kt` |
| 修改备份/恢复逻辑 | `help/storage/Backup.kt`、`help/storage/Restore.kt` |
| 修改 Web 服务 API | `web/HttpServer.kt`、`api/controller/` |
| 修改数据库表结构 | `data/AppDatabase.kt`（升版本）、`data/DatabaseMigrations.kt`（写迁移）、`data/entities/`（改实体）、`data/dao/`（改 DAO） |
| 修改通知栏样式 | `service/` 中对应服务、`res/layout/` |
| 修改全局对话框样式 | `lib/dialogs/`、`ui/widget/dialog/` |
| 修改设置页面 | `ui/config/` |
| 修改朗读引擎 | `service/TTSReadAloudService.kt`、`service/HttpReadAloudService.kt`、`help/TTS.kt` |
| 修改封面加载逻辑 | `model/BookCover.kt`、`help/glide/ImageLoader.kt` |
| 修改 JS 书源可用方法 | `help/JsExtensions.kt`、`help/rhino/NativeBaseSource.kt` |
| 修改 MOBI 解析 | `lib/mobi/` |
| 修改编码检测 | `lib/icu4j/`、`utils/EncodingDetect.kt` |
| 修改权限请求流程 | `lib/permission/` |
| 修改浮窗样式（背景/文字/圆角） | `ui/widget/ThemedPopupWindow.kt`、`lib/theme/MaterialValueHelper.kt`（`popupBackgroundColor`/`popupPrimaryTextColor`）、`utils/PopupThemeApplier.kt` |
| 修改 Compose 主题颜色映射 | `ui/common/compose/LegadoTheme.kt`（从 ThemeStore 读取，映射到 M3 ColorScheme） |
| 修改 Compose 封面加载 | `ui/common/compose/BookCoverImage.kt`（GlideImage + 默认封面 fallback） |
| 修改 Compose 卡片样式 | `ui/common/compose/SectionCard.kt`（圆角、内边距、背景色） |
