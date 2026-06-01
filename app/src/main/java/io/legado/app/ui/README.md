# 界面层

应用的所有 Activity、Fragment、ViewModel、Adapter 及自定义 UI 组件。按功能模块组织为 15 个子包，采用 MVVM 架构（ViewModel + ViewBinding）。

## 子目录

### `about/` — 关于页面
- **说明**: 应用信息展示、版本更新、崩溃日志查看、阅读记录统计
- **主要文件**: `AboutActivity`/`AboutFragment`（关于主页）、`UpdateDialog`（更新弹窗）、`AppLogDialog`（日志查看）、`CrashLogsDialog`（崩溃日志）、`ReadRecordActivity`（阅读记录）

### `association/` — 关联导入
- **说明**: 处理外部导入操作，包括书源、RSS 源、替换规则、词典规则、TTS 引擎、主题、TXT 目录规则的导入，以及 URL 确认、验证码处理
- **主要文件**: `OnLineImportActivity`（在线导入）、`FileAssociationActivity`（文件关联）、`OpenUrlConfirmActivity`（URL 确认）、`VerificationCodeActivity`（验证码）、各种 `Import*Dialog`/`Import*ViewModel`

### `book/` — 书籍相关界面
- **说明**: 书籍相关的核心功能界面，包含多个子包
  - `audio/` — 音频播放界面（`AudioPlayActivity`）
  - `bookmark/` — 书签管理
  - `cache/` — 缓存管理（`CacheActivity`）
  - `changecover/` — 封面换源
  - `changesource/` — 书籍换源
  - `explore/` — 书源发现页
  - `group/` — 书籍分组管理
  - `import/` — 本地书籍导入（`ImportBookActivity`）
  - `info/` — 书籍详情页（`BookInfoActivity`）
  - `manage/` — 书架管理（批量操作）
  - `manga/` — 漫画阅读界面（`ReadMangaActivity`）
  - `read/` — **核心阅读界面**（`ReadBookActivity`、`ReadMenu`、`ReadView`、`ContentTextView`、阅读配置弹窗等）
  - `search/` — 搜索界面（`SearchActivity`）
  - `searchContent/` — 搜索书籍内容
  - `source/` — 书源管理（`debug/` 调试、`edit/` 编辑、`manage/` 管理）
  - `toc/` — 目录界面

### `browser/` — 内置浏览器
- **说明**: 内置 WebView 浏览器，用于书源登录、网页查看等
- **主要文件**: `WebViewActivity`、`WebViewModel`

### `config/` — 设置配置
- **说明**: 应用设置页面，通过 `ConfigActivity` 统一承载，根据 `configTag` 路由到不同配置 Fragment
- **主要文件**: `ConfigActivity`（设置容器）、`ThemeConfigFragment`（主题设置）、`OtherConfigFragment`（其他设置）、`BackupConfigFragment`（备份设置）、`CoverConfigFragment`（封面设置）、`WelcomeConfigFragment`（欢迎页设置）、`CheckSourceConfig`（书源检测配置）、`DirectLinkUploadConfig`（直链上传配置）

### `dict/` — 词典查询
- **说明**: 划词翻译和词典查询功能
- **主要文件**: `DictDialog`（词典弹窗）、`DictViewModel`、`rule/`（词典规则管理）

### `file/` — 文件管理
- **说明**: 文件选择和处理界面
- **主要文件**: `FileManageActivity`（文件管理器）、`FilePickerDialog`（文件选择弹窗）、`HandleFileActivity`（文件处理）、`utils/`（文件工具）

### `font/` — 字体选择
- **说明**: 阅读字体选择和管理
- **主要文件**: `FontSelectDialog`（字体选择弹窗）、`FontAdapter`

### `login/` — 书源登录
- **说明**: 书源账号登录，支持 WebView 登录和验证码
- **主要文件**: `SourceLoginActivity`/`SourceLoginDialog`（登录界面）、`WebViewLoginFragment`（WebView 登录）、`SourceLoginViewModel`

### `main/` — 主界面
- **说明**: 应用主界面，底部导航栏切换书架、发现、RSS、我的四个标签页
- **主要文件**: `MainActivity`（主 Activity，承载 ViewPager + BottomNavigation）、`MainViewModel`、`MainFragmentInterface`（Fragment 接口）、`bookshelf/`（书架子页）、`explore/`（发现子页）、`rss/`（RSS 子页）、`my/`（我的子页）

### `qrcode/` — 二维码扫描
- **说明**: 二维码/条码扫描功能
- **主要文件**: `QrCodeActivity`/`QrCodeFragment`（扫描界面）、`QrCodeResult`（扫描结果）、`ScanResultCallback`

### `replace/` — 替换规则管理
- **说明**: 内容替换净化规则的管理界面
- **主要文件**: `ReplaceRuleActivity`（规则列表）、`ReplaceRuleAdapter`、`ReplaceRuleViewModel`、`edit/`（规则编辑）、`GroupManageDialog`（分组管理）

### `rss/` — RSS 订阅
- **说明**: RSS 订阅相关界面
  - `article/` — RSS 文章列表
  - `favorites/` — RSS 收藏
  - `read/` — RSS 文章阅读
  - `source/` — RSS 源管理
  - `subscription/` — RSS 订阅管理

### `welcome/` — 欢迎页
- **说明**: 应用启动欢迎/闪屏页面
- **主要文件**: `WelcomeActivity`（LAUNCHER Activity，应用入口）

### `widget/` — 自定义 UI 组件库
- **说明**: 应用自定义的 UI 控件和可复用组件，包含多个子包
  - `anima/` — 动画控件（`RefreshProgressBar` 刷新进度条、`RotateLoading` 旋转加载、`explosion_field/` 爆炸动画）
  - `checkbox/` — 自定义复选框（`SmoothCheckBox` 带平滑动画）
  - `code/` — 代码编辑器（`CodeView` 语法高亮，用于书源编辑）
  - `dialog/` — 通用对话框（`TextDialog` 文本/Markdown/HTML 展示、`CodeDialog` 代码展示、`PhotoDialog` 图片展示、`TextListDialog` 列表选择、`UrlOptionDialog` URL 选项、`VariableDialog` 变量输入、`WaitDialog` 加载等待）
  - `dynamiclayout/` — 状态切换布局（`DynamicFrameLayout` 支持内容/进度/错误/空状态切换）
  - `image/` — 图片控件（`CoverImageView` 书籍封面 5:7 比例、`PhotoView` 可缩放图片、`CircleImageView` 圆形图片、`FilletImageView` 圆角图片）
  - `keyboard/` — 键盘辅助（`KeyboardToolPop` 阅读页键盘工具栏、`KeyboardAssistsConfig` 快捷配置）
  - `number/` — 数字选择器（`NumberPickerDialog`）
  - `recycler/` — RecyclerView 辅助（`ItemTouchCallback` 拖拽/滑动、`DragSelectTouchHelper` 拖选、`DividerNoLast`/`VerticalDivider` 分割线、`LoadMoreView` 加载更多、`NoChildScrollLinearLayoutManager`/`UpLinearLayoutManager` 布局管理器）
  - `seekbar/` — 滑块控件（`VerticalSeekBar` 垂直滑块，用于亮度调节）
  - `text/` — 文本控件（`ScrollTextView` 嵌套滚动文本、`AccentTextView`/`PrimaryTextView`/`SecondaryTextView` 主题色文本、`BadgeView` 角标、`StrokeTextView` 描边文本、`BevelLabelView` 斜角标签、`TextInputLayout` 输入框、`AutoCompleteTextView` 自动补全）
  - 其他顶层文件：`TitleBar`（标题栏，封装 Toolbar）、`SelectActionBar`（多选操作栏）、`SearchView`（搜索框）、`LabelsBar`（标签栏）、`DetailSeekBar`（进度条）、`BatteryView`（电池图标）、`ReadBarChartView`（阅读柱状图）、`ReaderInfoBarView`（阅读信息栏）、`ReadHeatmapView`（阅读热力图）、`ShadowLayout`（阴影布局）、`RoundedSpinner`（圆角下拉选择器）


---

## Compose 迁移评估

### ✅ 可安全删除的文件（共 15 个）

**Style1 书架（5 个 Kotlin + 2 个 XML）**
| 文件 | 原因 |
|---|---|
| `ui/main/bookshelf/style1/BookshelfFragment1.kt` | 已被 `BookshelfComposeFragment` 替代 |
| `ui/main/bookshelf/style1/books/BooksFragment.kt` | 内部 Fragment，不再使用 |
| `ui/main/bookshelf/style1/books/BooksAdapterList.kt` | RecyclerView Adapter |
| `ui/main/bookshelf/style1/books/BooksAdapterGrid.kt` | RecyclerView Adapter |
| `ui/main/bookshelf/style1/books/BaseBooksAdapter.kt` | Adapter 基类 |
| `res/layout/fragment_bookshelf1.xml` | Style1 布局 |
| `res/layout/item_bookshelf_list.xml` | 列表 item 布局 |

**Style2 书架（4 个 Kotlin + 2 个 XML）**
| 文件 | 原因 |
|---|---|
| `ui/main/bookshelf/style2/BookshelfFragment2.kt` | 已被 `BookshelfComposeFragment` 替代 |
| `ui/main/bookshelf/style2/BooksAdapterList.kt` | RecyclerView Adapter |
| `ui/main/bookshelf/style2/BooksAdapterGrid.kt` | RecyclerView Adapter |
| `ui/main/bookshelf/style2/BaseBooksAdapter.kt` | Adapter 基类 |
| `res/layout/fragment_bookshelf2.xml` | Style2 布局 |
| `res/layout/item_bookshelf_grid.xml` | 网格 item 布局 |

**Style2 分组相关 XML（2 个）**
| 文件 | 原因 |
|---|---|
| `res/layout/item_bookshelf_list_group.xml` | Style2 分组头布局 |
| `res/layout/item_bookshelf_grid_group.xml` | Style2 分组头布局 |

---

### ⚠️ 暂时不能删除的文件

**旧版 `BookInfoActivity` + `BookInfoViewModel`**（2 个 Kotlin + 1 个 XML）

虽然书架、搜索、阅读页等**主入口**已切换到 `BookInfoComposeActivity`，但以下文件仍残留旧 `BookInfoActivity` 的 import 或间接引用：

- `ReadBookActivity.kt` — import 了旧版（实际已用 Compose 版）
- `SearchActivity.kt` — import 了旧版（实际已用 Compose 版）
- `ExploreShowActivity.kt` — 可能仍直接引用
- `AddToBookshelfDialog.kt` — 可能仍直接引用
- BookshelfManageActivity.kt — 可能仍直接引用
- `ReadMangaActivity.kt` — 可能仍直接引用

> **建议**：先将上述文件中残留的 `BookInfoActivity` import 和启动代码统一替换为 `BookInfoComposeActivity`，确认无遗漏后再删除旧版。

**`BookInfoEditActivity`** — 独立功能页（编辑书籍信息），Compose 版详情页仍通过 `infoEditResult.launch` 调用它，**不能删**。

---

### 📋 删除步骤建议

1. **先删 Style1/Style2 整个目录**（15 个文件）
2. **更新 `BaseBookshelfFragment`** — 移除对 Style1/Style2 子类的引用（如有抽象方法）
3. **清理各入口文件的残留 import**（`ReadBookActivity`、`SearchActivity` 等）
4. **验证编译通过后**，再删除旧 `BookInfoActivity`、`BookInfoViewModel`、`activity_book_info.xml`
5. **清理 `AndroidManifest.xml`** — 移除旧 `BookInfoActivity` 的声明
