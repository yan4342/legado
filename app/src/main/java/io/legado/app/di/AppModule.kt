package io.legado.app.di

import io.legado.app.ui.about.CrashViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.rss.RssViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::SearchViewModel)
    viewModelOf(::ExploreShowViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::BookshelfViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::RssViewModel)
    viewModelOf(::DictViewModel)
    viewModelOf(::CrashViewModel)
}
