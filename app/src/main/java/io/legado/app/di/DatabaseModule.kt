package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> { appDb }
    factory { get<AppDatabase>().bookDao }
    factory { get<AppDatabase>().bookGroupDao }
    factory { get<AppDatabase>().bookSourceDao }
    factory { get<AppDatabase>().bookSourceVersionDao }
    factory { get<AppDatabase>().bookChapterDao }
    factory { get<AppDatabase>().replaceRuleDao }
    factory { get<AppDatabase>().searchBookDao }
    factory { get<AppDatabase>().searchKeywordDao }
    factory { get<AppDatabase>().rssSourceDao }
    factory { get<AppDatabase>().bookmarkDao }
    factory { get<AppDatabase>().rssArticleDao }
    factory { get<AppDatabase>().rssStarDao }
    factory { get<AppDatabase>().rssReadRecordDao }
    factory { get<AppDatabase>().cookieDao }
    factory { get<AppDatabase>().txtTocRuleDao }
    factory { get<AppDatabase>().readRecordDao }
    factory { get<AppDatabase>().dailyReadRecordDao }
    factory { get<AppDatabase>().hourlyReadRecordDao }
    factory { get<AppDatabase>().httpTTSDao }
    factory { get<AppDatabase>().cacheDao }
    factory { get<AppDatabase>().ruleSubDao }
    factory { get<AppDatabase>().dictRuleDao }
    factory { get<AppDatabase>().aiDictRuleDao }
    factory { get<AppDatabase>().keyboardAssistsDao }
    factory { get<AppDatabase>().serverDao }
    factory { get<AppDatabase>().aiProfileDao }
    factory { get<AppDatabase>().aiArtifactDao }
    factory { get<AppDatabase>().aiChatDao }
    factory { get<AppDatabase>().aiHtmlAppDao }
    factory { get<AppDatabase>().aiCharacterCardDao }
    factory { get<AppDatabase>().aiMemoryDao }
    factory { get<AppDatabase>().aiWritingPromptDao }
    factory { get<AppDatabase>().aiWorldBookDao }
    factory { get<AppDatabase>().aiToolConfigDao }
    factory { get<AppDatabase>().aiSkillDao }
    factory { get<AppDatabase>().aiMemoryTableDao }
    factory { get<AppDatabase>().aiPromptTemplateDao }
    factory { get<AppDatabase>().aiOutlineDao }
    factory { get<AppDatabase>().aiPlanDao }
    factory { get<AppDatabase>().aiTodoDao }
    factory { get<AppDatabase>().aiBookOutlineDao }
    factory { get<AppDatabase>().aiUsageRecordDao }
    factory { get<AppDatabase>().aiStructuredDataSnapshotDao }
    factory { get<AppDatabase>().aiWorkspaceDao }
    factory { get<AppDatabase>().aiPromptPipelinePresetDao }
    factory { get<AppDatabase>().aiWorldBookEntryDao }
    factory { get<AppDatabase>().bookCharacterCastDao }
    factory { get<AppDatabase>().readAloudVoiceDao }
    factory { get<AppDatabase>().chapterSpeechDao }
    factory { get<AppDatabase>().cloudTtsEngineDao }
}
