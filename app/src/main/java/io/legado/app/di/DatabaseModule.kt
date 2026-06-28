package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> { appDb }
    factory { get<AppDatabase>().bookDao }
    factory { get<AppDatabase>().bookGroupDao }
    factory { get<AppDatabase>().bookSourceDao }
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
}
