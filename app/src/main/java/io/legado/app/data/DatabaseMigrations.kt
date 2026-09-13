package io.legado.app.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
object DatabaseMigrations {

    val migrations: Array<Migration> by lazy {
        arrayOf(
            migration_10_11, migration_11_12, migration_12_13, migration_13_14,
            migration_14_15, migration_15_17, migration_17_18, migration_18_19,
            migration_19_20, migration_20_21, migration_21_22, migration_22_23,
            migration_23_24, migration_24_25, migration_25_26, migration_26_27,
            migration_27_28, migration_28_29, migration_29_30, migration_30_31,
            migration_31_32, migration_32_33, migration_33_34, migration_34_35,
            migration_35_36, migration_36_37, migration_37_38, migration_38_39,
            migration_39_40, migration_40_41, migration_41_42, migration_42_43,
            migration_76_77, migration_77_78, migration_78_79, migration_79_80,
            migration_82_83,
            migration_95_96,
            migration_96_97,
            migration_97_98,
            migration_98_99,
            migration_99_100,
            migration_100_101,
            migration_101_102,
            migration_102_103,
            migration_103_104,
            migration_104_105,
            migration_106_107,
            migration_107_108,
        )
    }

    private val migration_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE txtTocRules")
            db.execSQL(
                """CREATE TABLE txtTocRules(id INTEGER NOT NULL, 
                    name TEXT NOT NULL, rule TEXT NOT NULL, serialNumber INTEGER NOT NULL, 
                    enable INTEGER NOT NULL, PRIMARY KEY (id))"""
            )
        }
    }

    private val migration_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD style TEXT ")
        }
    }

    private val migration_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD articleStyle INTEGER NOT NULL DEFAULT 0 ")
        }
    }

    private val migration_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL,
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, `coverUrl` TEXT, 
                    `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, 
                    `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, `lastCheckCount` INTEGER NOT NULL, 
                    `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, `durChapterPos` INTEGER NOT NULL, 
                    `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, `order` INTEGER NOT NULL, 
                    `originOrder` INTEGER NOT NULL, `useReplaceRule` INTEGER NOT NULL, `variable` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            db.execSQL("INSERT INTO books_new select * from books ")
            db.execSQL("DROP TABLE books")
            db.execSQL("ALTER TABLE books_new RENAME TO books")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks ADD bookAuthor TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_15_17 = object : Migration(15, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `readRecord` (`bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`bookName`))")
        }
    }

    private val migration_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `httpTTS` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    private val migration_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `readRecordNew` (`androidId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, 
                    PRIMARY KEY(`androidId`, `bookName`))"""
            )
            db.execSQL("INSERT INTO readRecordNew(androidId, bookName, readTime) select '${AppConst.androidId}' as androidId, bookName, readTime from readRecord")
            db.execSQL("DROP TABLE readRecord")
            db.execSQL("ALTER TABLE readRecordNew RENAME TO readRecord")
        }
    }
    private val migration_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE book_sources ADD bookSourceComment TEXT")
        }
    }

    private val migration_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE book_groups ADD show INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val migration_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL, 
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, 
                    `coverUrl` TEXT, `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, 
                    `group` INTEGER NOT NULL, `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, 
                    `lastCheckCount` INTEGER NOT NULL, `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, 
                    `durChapterPos` INTEGER NOT NULL, `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, 
                    `order` INTEGER NOT NULL, `originOrder` INTEGER NOT NULL, `variable` TEXT, `readConfig` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            db.execSQL(
                """INSERT INTO books_new select `bookUrl`, `tocUrl`, `origin`, `originName`, `name`, `author`, `kind`, `customTag`, `coverUrl`, 
                    `customCoverUrl`, `intro`, `customIntro`, `charset`, `type`, `group`, `latestChapterTitle`, `latestChapterTime`, `lastCheckTime`, 
                    `lastCheckCount`, `totalChapterNum`, `durChapterTitle`, `durChapterIndex`, `durChapterPos`, `durChapterTime`, `wordCount`, `canUpdate`, 
                    `order`, `originOrder`, `variable`, null
                    from books"""
            )
            db.execSQL("DROP TABLE books")
            db.execSQL("ALTER TABLE books_new RENAME TO books")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD baseUrl TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `caches` (`key` TEXT NOT NULL, `value` TEXT, `deadline` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_caches_key` ON `caches` (`key`)")
        }
    }

    private val migration_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sourceSubs` 
                    (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, `customOrder` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`))"""
            )
        }
    }

    private val migration_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `ruleSubs` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, 
                    `customOrder` INTEGER NOT NULL, `autoUpdate` INTEGER NOT NULL, `update` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
            )
            db.execSQL(" insert into `ruleSubs` select *, 0, 0 from `sourceSubs` ")
            db.execSQL("DROP TABLE `sourceSubs`")
        }
    }

    private val migration_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(" ALTER TABLE rssSources ADD singleUrl INTEGER NOT NULL DEFAULT 0 ")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `bookmarks1` (`time` INTEGER NOT NULL, `bookUrl` TEXT NOT NULL, `bookName` TEXT NOT NULL, 
                        `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, 
                        `bookText` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`time`))"""
            )
            db.execSQL(
                """insert into `bookmarks1` 
                        select `time`, `bookUrl`, `bookName`, `bookAuthor`, `chapterIndex`, `pageIndex`, `chapterName`, '', `content` 
                        from bookmarks"""
            )
            db.execSQL(" DROP TABLE `bookmarks` ")
            db.execSQL(" ALTER TABLE bookmarks1 RENAME TO bookmarks ")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_time` ON `bookmarks` (`time`)")
        }
    }

    private val migration_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssArticles ADD variable TEXT")
            db.execSQL("ALTER TABLE rssStars ADD variable TEXT")
        }
    }

    private val migration_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD sourceComment TEXT")
        }
    }

    private val migration_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD `startFragmentId` TEXT")
            db.execSQL("ALTER TABLE chapters ADD `endFragmentId` TEXT")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `epubChapters` 
                    (`bookUrl` TEXT NOT NULL, `href` TEXT NOT NULL, `parentHref` TEXT, 
                    PRIMARY KEY(`bookUrl`, `href`), FOREIGN KEY(`bookUrl`) REFERENCES `books`(`bookUrl`) ON UPDATE NO ACTION ON DELETE CASCADE )
                """
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_epubChapters_bookUrl` ON `epubChapters` (`bookUrl`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epubChapters_bookUrl_href` ON `epubChapters` (`bookUrl`, `href`)")
        }
    }

    private val migration_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE readRecord RENAME TO readRecord1")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `readRecord` (`deviceId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`deviceId`, `bookName`))
                """
            )
            db.execSQL("insert into readRecord (deviceId, bookName, readTime) select androidId, bookName, readTime from readRecord1")
        }
    }

    private val migration_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE `epubChapters`")
        }
    }

    private val migration_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_old")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (`time` INTEGER NOT NULL,
                    `bookName` TEXT NOT NULL, `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, 
                    `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, `bookText` TEXT NOT NULL, 
                    `content` TEXT NOT NULL, PRIMARY KEY(`time`))
                """
            )
            db.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS `index_bookmarks_bookName_bookAuthor` ON `bookmarks` (`bookName`, `bookAuthor`)
                """
            )
            db.execSQL(
                """
                    insert into bookmarks (time, bookName, bookAuthor, chapterIndex, chapterPos, chapterName, bookText, content)
                    select time, ifNull(b.name, bookName) bookName, ifNull(b.author, bookAuthor) bookAuthor, 
                    chapterIndex, chapterPos, chapterName, bookText, content from bookmarks_old o
                    left join books b on o.bookUrl = b.bookUrl
                """
            )
        }
    }

    private val migration_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_groups` ADD `cover` TEXT")
        }
    }

    private val migration_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `book_sources` ADD`loginCheckJs` TEXT")
        }
    }

    private val migration_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rssSources` ADD `loginUrl` TEXT")
            db.execSQL("ALTER TABLE `rssSources` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `rssSources` ADD `loginCheckJs` TEXT")
        }
    }

    private val migration_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `respondTime` INTEGER NOT NULL DEFAULT 180000")
        }
    }

    private val migration_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rssSources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chapters` ADD `isVip` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `chapters` ADD `isPay` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginUrl` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginCheckJs` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `header` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'httpTTS' ADD `contentType` TEXT")
        }
    }

    private val migration_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chapters` ADD `isVolume` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_76_77 = object : Migration(76, 77) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `aiDictRules` (
                    `name` TEXT NOT NULL, `endpoint` TEXT NOT NULL, `apiKey` TEXT NOT NULL,
                    `model` TEXT NOT NULL, `systemPrompt` TEXT NOT NULL,
                    `userPromptTemplate` TEXT NOT NULL, `temperature` REAL NOT NULL,
                    `maxTokens` INTEGER NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1,
                    `sortNumber` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`name`))"""
            )
        }
    }

    private val migration_77_78 = object : Migration(77, 78) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `aiDictRules` ADD `extraJson` TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_78_79 = object : Migration(78, 79) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // schema unchanged; bumped to regenerate identity hash
        }
    }

    private val migration_79_80 = object : Migration(79, 80) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // entity @ColumnInfo adjusted to match migration-created table defaults
        }
    }

    private val migration_82_83 = object : Migration(82, 83) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE ai_character_cards(
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    openingLine TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE ai_writing_prompts(
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    category TEXT NOT NULL DEFAULT 'style',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN type TEXT NOT NULL DEFAULT 'chat'")
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN characterCardId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN promptIds TEXT DEFAULT NULL")
        }
    }

    @Suppress("ClassName")
    class Migration_54_55 : AutoMigrationSpec {

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                update books set type = ${BookType.audio}
                where type = ${BookSourceType.audio}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.image}
                where type = ${BookSourceType.image}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.webFile}
                where type = ${BookSourceType.file}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.text}
                where type = ${BookSourceType.default}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = type | ${BookType.local}
                where origin like '${BookType.localTag}%' or origin like '${BookType.webDavTag}%'
            """.trimIndent()
            )
        }

    }


    @Suppress("ClassName")
    @DeleteColumn(
        tableName = "book_sources",
        columnName = "enabledReview"
    )
    class Migration_64_65 : AutoMigrationSpec

    private val migration_95_96 = object : Migration(95, 96) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM ai_writing_prompts WHERE category = 'action_help_reply'")
            db.execSQL(
                "DELETE FROM ai_prompt_templates WHERE promptKey IN ('writing_action_help_reply_roleplay', 'writing_action_help_reply_author', 'history_memory_table_header')"
            )
        }
    }

    private val migration_96_97 = object : Migration(96, 97) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN userName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN userDescription TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN userCardEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_97_98 = object : Migration(97, 98) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_workspaces(
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    characterCardIds TEXT NOT NULL DEFAULT '',
                    worldBookIds TEXT NOT NULL DEFAULT '',
                    writingPromptIds TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_ai_workspaces_conversationId ON ai_workspaces(conversationId)"
            )
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN workspaceId TEXT DEFAULT NULL")

            // Backfill one workspace per writing conversation
            db.query(
                "SELECT id, title, characterCardId, characterCardIds, promptIds FROM ai_chat_conversations WHERE type = 'writing'"
            ).use { cursor ->
                val idIdx = cursor.getColumnIndex("id")
                val titleIdx = cursor.getColumnIndex("title")
                val cardIdIdx = cursor.getColumnIndex("characterCardId")
                val cardIdsIdx = cursor.getColumnIndex("characterCardIds")
                val promptIdsIdx = cursor.getColumnIndex("promptIds")
                while (cursor.moveToNext()) {
                    val convId = cursor.getString(idIdx)
                    val title = cursor.getString(titleIdx).orEmpty()
                    val cardIdsRaw = cursor.getString(cardIdsIdx)
                    val singleCard = cursor.getString(cardIdIdx)
                    val cardIds = normalizeIdList(
                        when {
                            !cardIdsRaw.isNullOrBlank() -> cardIdsRaw
                            !singleCard.isNullOrBlank() -> singleCard
                            else -> ""
                        }
                    )
                    val promptIds = normalizeIdList(cursor.getString(promptIdsIdx).orEmpty())
                    val worldBookIds = resolveWorldBookIdsFromCards(db, cardIds)
                    val now = System.currentTimeMillis()
                    val wsId = "ws_${convId.replace("-", "").takeLast(16)}_$now"
                    db.execSQL(
                        """INSERT OR REPLACE INTO ai_workspaces
                            (id, name, conversationId, characterCardIds, worldBookIds, writingPromptIds, createdAt, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                        arrayOf(wsId, title.ifBlank { "Workspace" }, convId, cardIds, worldBookIds, promptIds, now, now)
                    )
                    db.execSQL(
                        "UPDATE ai_chat_conversations SET workspaceId = ? WHERE id = ?",
                        arrayOf(wsId, convId)
                    )
                }
            }
        }

        private fun resolveWorldBookIdsFromCards(db: SupportSQLiteDatabase, cardIdsCsv: String): String {
            if (cardIdsCsv.isBlank()) return ""
            val ids = parseIdList(cardIdsCsv)
            if (ids.isEmpty()) return ""
            val collected = linkedSetOf<String>()
            for (cardId in ids) {
                db.query(
                    "SELECT worldBookIds FROM ai_character_cards WHERE id = ?",
                    arrayOf(cardId)
                ).use { c ->
                    if (c.moveToFirst()) {
                        parseIdList(c.getString(0).orEmpty()).forEach { collected.add(it) }
                    }
                }
            }
            return collected.joinToString(",")
        }

        /** Accept JSON array or CSV/semicolon lists; emit CSV for workspace storage. */
        private fun normalizeIdList(raw: String?): String = parseIdList(raw).joinToString(",")

        private fun parseIdList(raw: String?): List<String> {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return emptyList()
            if (text.startsWith("[")) {
                // Lightweight JSON string-array parse without depending on GSON in migrations.
                val inner = text.removePrefix("[").removeSuffix("]")
                return inner.split(',')
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotEmpty() }
                    .distinct()
            }
            return text.split(',', ';')
                .map { it.trim().trim('"', '\'', '[', ']') }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    }

    private val migration_98_99 = object : Migration(98, 99) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_prompt_pipeline_presets(
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    blocksJson TEXT NOT NULL,
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN personality TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN scenario TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN exampleDialogues TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN postHistoryInstructions TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN alternateOpenings TEXT NOT NULL DEFAULT '[]'")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_world_book_entries(
                    id TEXT NOT NULL PRIMARY KEY,
                    worldBookId TEXT NOT NULL,
                    name TEXT NOT NULL DEFAULT '',
                    keys TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    constant INTEGER NOT NULL DEFAULT 0,
                    priority INTEGER NOT NULL DEFAULT 100,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(worldBookId) REFERENCES ai_world_books(id) ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_world_book_entries_worldBookId ON ai_world_book_entries(worldBookId)"
            )
        }
    }

    private val migration_99_100 = object : Migration(99, 100) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ai_world_book_entries ADD COLUMN position TEXT NOT NULL DEFAULT 'prefix'"
            )
            db.execSQL(
                "ALTER TABLE ai_world_book_entries ADD COLUMN insertDepth INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE ai_world_book_entries ADD COLUMN role TEXT NOT NULL DEFAULT 'system'"
            )
            db.execSQL(
                "ALTER TABLE ai_world_book_entries ADD COLUMN scanDepth INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS book_source_versions(
                    id TEXT NOT NULL PRIMARY KEY,
                    bookSourceUrl TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    source TEXT NOT NULL,
                    diffSummary TEXT,
                    toolCallId TEXT,
                    batchId TEXT,
                    conversationId TEXT,
                    createdAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_source_versions_bookSourceUrl ON book_source_versions(bookSourceUrl)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_source_versions_createdAt ON book_source_versions(createdAt)"
            )
        }
    }

    private val migration_100_101 = object : Migration(100, 101) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN aliasesJson TEXT NOT NULL DEFAULT '[]'"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN voiceGender TEXT NOT NULL DEFAULT 'unknown'"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN voiceAgeBand TEXT NOT NULL DEFAULT 'unknown'"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS book_character_cast(
                    bookUrl TEXT NOT NULL,
                    characterCardId TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    dramaticRole TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(bookUrl, characterCardId),
                    FOREIGN KEY(characterCardId) REFERENCES ai_character_cards(id) ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_character_cast_bookUrl ON book_character_cast(bookUrl)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_character_cast_characterCardId ON book_character_cast(characterCardId)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS read_aloud_voices(
                    id TEXT NOT NULL,
                    engineType TEXT NOT NULL,
                    engineId TEXT NOT NULL DEFAULT '',
                    speakerId TEXT NOT NULL DEFAULT '',
                    displayName TEXT NOT NULL,
                    traitsJson TEXT NOT NULL DEFAULT '[]',
                    emotionCatalogJson TEXT NOT NULL DEFAULT '[]',
                    managedBy TEXT NOT NULL DEFAULT 'user',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    available INTEGER NOT NULL DEFAULT 1,
                    revision INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_read_aloud_voices_engineType_engineId_speakerId ON read_aloud_voices(engineType, engineId, speakerId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_read_aloud_voices_enabled_displayName ON read_aloud_voices(enabled, displayName)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS book_voice_bindings(
                    bookUrl TEXT NOT NULL,
                    subjectType TEXT NOT NULL,
                    subjectId TEXT NOT NULL,
                    voiceId TEXT NOT NULL,
                    locked INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT 'user',
                    confidence REAL NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(bookUrl, subjectType, subjectId)
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_voice_bindings_bookUrl_subjectType ON book_voice_bindings(bookUrl, subjectType)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_book_voice_bindings_voiceId ON book_voice_bindings(voiceId)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chapter_speech_analysis(
                    id TEXT NOT NULL,
                    bookUrl TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    contentHash TEXT NOT NULL,
                    resolverVersion TEXT NOT NULL,
                    characterRevision TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT 'pending',
                    error TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_chapter_speech_analysis_bookUrl_chapterIndex_contentHash_resolverVersion ON chapter_speech_analysis(bookUrl, chapterIndex, contentHash, resolverVersion)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chapter_speech_analysis_bookUrl_chapterIndex_updatedAt ON chapter_speech_analysis(bookUrl, chapterIndex, updatedAt)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chapter_speech_segments(
                    id TEXT NOT NULL,
                    analysisId TEXT NOT NULL,
                    bookUrl TEXT NOT NULL,
                    chapterIndex INTEGER NOT NULL,
                    paragraphIndex INTEGER NOT NULL,
                    start INTEGER NOT NULL,
                    end INTEGER NOT NULL,
                    chapterPosition INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    roleType TEXT NOT NULL,
                    characterId TEXT,
                    characterName TEXT NOT NULL DEFAULT '',
                    emotion TEXT NOT NULL DEFAULT '',
                    confidence REAL NOT NULL DEFAULT 0,
                    source TEXT NOT NULL,
                    userLocked INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_chapter_speech_segments_analysisId_paragraphIndex_start_end ON chapter_speech_segments(analysisId, paragraphIndex, start, end)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chapter_speech_segments_bookUrl_chapterIndex_paragraphIndex ON chapter_speech_segments(bookUrl, chapterIndex, paragraphIndex)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chapter_speech_segments_characterId ON chapter_speech_segments(characterId)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS cloud_tts_engines(
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    baseUrl TEXT NOT NULL DEFAULT '',
                    apiKey TEXT NOT NULL DEFAULT '',
                    secretKey TEXT NOT NULL DEFAULT '',
                    region TEXT NOT NULL DEFAULT '',
                    appId TEXT NOT NULL DEFAULT '',
                    model TEXT NOT NULL DEFAULT '',
                    optionsJson TEXT NOT NULL DEFAULT '{}',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cloud_tts_engines_provider_enabled ON cloud_tts_engines(provider, enabled)"
            )
            db.execSQL(
                "ALTER TABLE ai_chat_conversations ADD COLUMN writingSubMode TEXT NOT NULL DEFAULT 'roleplay'"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN bookUrl TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN bookName TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN bookAuthor TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_character_cards ADD COLUMN avatarPath TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_memory_tables ADD COLUMN bookUrl TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_memory_tables ADD COLUMN bookName TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_memory_tables ADD COLUMN bookAuthor TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_outlines ADD COLUMN bookUrl TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_outlines ADD COLUMN bookName TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE ai_outlines ADD COLUMN bookAuthor TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    private val migration_101_102 = object : Migration(101, 102) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_skills(
                    skillId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    mode TEXT NOT NULL DEFAULT 'chat',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    toolNamesJson TEXT NOT NULL DEFAULT '[]',
                    docIdsJson TEXT NOT NULL DEFAULT '[]',
                    hint TEXT NOT NULL DEFAULT '',
                    instruction TEXT NOT NULL DEFAULT '',
                    builtin INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(skillId)
                )"""
            )
        }
    }

    private val migration_102_103 = object : Migration(102, 103) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ai_chat_conversations ADD COLUMN skillIds TEXT",
            )
        }
    }

    private val migration_103_104 = object : Migration(103, 104) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ai_chat_conversations ADD COLUMN draftText TEXT NOT NULL DEFAULT ''",
            )
            db.execSQL(
                "ALTER TABLE ai_chat_messages ADD COLUMN excludeFromContext INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    private val migration_104_105 = object : Migration(104, 105) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ai_chat_conversations ADD COLUMN compressedSummary TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    private val migration_106_107 = object : Migration(106, 107) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ---- 正典/衍生分层：实体加 canonical / bookUrl / forkedFromCardId 列 ----
            db.execSQL("ALTER TABLE ai_workspaces ADD COLUMN bookUrl TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ai_memory_tables ADD COLUMN canonical INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_world_books ADD COLUMN canonical INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN canonical INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_character_cards ADD COLUMN forkedFromCardId TEXT NOT NULL DEFAULT ''")

            // ---- 正典大纲新表（PK=bookUrl） ----
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_book_outlines(
                    bookUrl TEXT NOT NULL PRIMARY KEY,
                    content TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    sourceConversationId TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL
                )"""
            )

            // ---- 回填：已有 bookUrl 绑定的行置正典 ----
            db.execSQL("UPDATE ai_memory_tables SET canonical = 1 WHERE bookUrl != ''")
            db.execSQL("UPDATE ai_world_books SET canonical = 1 WHERE bookUrl != ''")
            db.execSQL("UPDATE ai_character_cards SET canonical = 1 WHERE bookUrl != ''")

            // ---- 回填 ai_book_outlines：按书从 ai_outlines 取最新一份 ----
            // INSERT OR REPLACE 以 bookUrl 为主键，ORDER BY updatedAt DESC 保证最新一份最后写入并被保留。
            db.execSQL(
                """INSERT OR REPLACE INTO ai_book_outlines(bookUrl, content, enabled, sourceConversationId, updatedAt)
                   SELECT bookUrl, content, enabled, conversationId, updatedAt
                   FROM ai_outlines
                   WHERE bookUrl != ''
                   ORDER BY updatedAt DESC"""
            )

            // ---- 会话级 AI 输出审批模式（ask=原 confirmToolsBeforeExecute=true） ----
            db.execSQL("ALTER TABLE ai_chat_conversations ADD COLUMN outputMode TEXT NOT NULL DEFAULT 'ask'")

            // ---- 计划工件表：独立于普通回复的审批计划，内容以 plan_<id>.md 文件为唯一来源 ----
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_plans(
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL,
                    messageId TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    revision INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_plans_conversationId ON ai_plans(conversationId)"
            )
        }
    }

    private val migration_107_108 = object : Migration(107, 108) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ---- 会话任务清单：AI 通过 update_todos 全量维护，每会话一行 ----
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_todos(
                    conversationId TEXT NOT NULL PRIMARY KEY,
                    todosJson TEXT NOT NULL DEFAULT '[]',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )"""
            )

            // ---- AI 生成的 HTML 应用（游戏等）元数据；源码存文件 html_apps/{id}.html ----
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS ai_html_apps(
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL,
                    messageId TEXT NOT NULL DEFAULT '',
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_html_apps_conversationId ON ai_html_apps(conversationId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_html_apps_messageId ON ai_html_apps(messageId)"
            )
        }
    }

}