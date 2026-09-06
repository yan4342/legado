package io.legado.app.di

import io.legado.app.data.repository.AiArtifactRepository
import io.legado.app.data.repository.AiCharacterCardRepository
import io.legado.app.data.repository.AiChatRepository
import io.legado.app.data.repository.CacheFirstAiTextGateway
import io.legado.app.data.repository.AiMemoryRepository
import io.legado.app.data.repository.AiProfileRepository
import io.legado.app.data.repository.AiTextRepositoryImpl
import io.legado.app.data.repository.AiUsageRepository
import io.legado.app.data.repository.MeteringAiTextGateway
import io.legado.app.data.repository.AiMemoryTableRepository
import io.legado.app.data.repository.AiBookOutlineRepository
import io.legado.app.data.repository.AiHtmlAppRepository
import io.legado.app.data.repository.AiOutlineRepository
import io.legado.app.data.repository.AiPlanRepository
import io.legado.app.data.repository.AiTodoRepository
import io.legado.app.data.repository.AiPromptTemplateRepository
import io.legado.app.data.repository.AiSkillRepository
import io.legado.app.data.repository.AiToolConfigRepository
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.AiWritingPromptRepository
import io.legado.app.data.repository.AiWorldBookRepository
import io.legado.app.data.repository.AiWorkspaceRepository
import io.legado.app.data.repository.DirectLinkUploadRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiBookOutlineGateway
import io.legado.app.domain.gateway.AiHtmlAppGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiPlanGateway
import io.legado.app.domain.gateway.AiTodoGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AiWritingPromptGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.AiEvalJsUseCase
import io.legado.app.domain.usecase.ImportWorldInfoUseCase
import io.legado.app.domain.usecase.ExtractWorldBookUseCase
import io.legado.app.domain.usecase.BookshelfAccessPreviewer
import io.legado.app.domain.usecase.AddBookToBookshelfUseCase
import io.legado.app.domain.usecase.SearchBookSourcesUseCase
import io.legado.app.domain.usecase.SearchBookContentUseCase
import io.legado.app.domain.usecase.SearchWorkspaceUseCase
import io.legado.app.domain.usecase.BookSourceVersionService
import io.legado.app.domain.usecase.BookSourceAgentTools
import io.legado.app.domain.usecase.FetchWebPageUseCase
import io.legado.app.domain.usecase.WebSearchRateLimiter
import io.legado.app.domain.usecase.WebSearchUseCase
import io.legado.app.domain.usecase.CheckBookSourceUseCase
import io.legado.app.domain.usecase.SearchRuleHelpUseCase
import io.legado.app.domain.usecase.WriteBookSourceFromUrlUseCase
import io.legado.app.domain.usecase.ResolveBookChapterContentUseCase
import io.legado.app.domain.usecase.WritingStructuredMaintainUseCase
import io.legado.app.data.repository.CharacterCardSpeakerRepository
import io.legado.app.data.repository.ChapterSpeechRepository
import io.legado.app.data.repository.CloudTtsEngineRepository
import io.legado.app.data.repository.HttpTtsEngineRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadAloudVoiceRepository
import io.legado.app.data.security.CloudTtsCredentialCipher
import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.usecase.AnalyzeChapterSpeechUseCase
import io.legado.app.domain.usecase.BuildSpeechPlanUseCase
import io.legado.app.domain.usecase.IdentifyBookCharactersUseCase
import io.legado.app.domain.usecase.PrepareChapterSpeechPlanUseCase
import io.legado.app.domain.usecase.RefineSpeechWithAiUseCase
import io.legado.app.domain.usecase.ResolveLocalSpeakersUseCase
import io.legado.app.domain.usecase.ExportCloudTtsAsHttpTtsUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.domain.usecase.SyncVoicesFromAllEnginesUseCase
import io.legado.app.domain.usecase.SkillTools
import io.legado.app.domain.usecase.PlanFileTools
import io.legado.app.domain.usecase.TodoTools
import io.legado.app.domain.usecase.HtmlAppTools
import io.legado.app.domain.usecase.TtsConfigTools
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.HttpTtsStreamFetcher
import io.legado.app.help.readaloud.playback.ReadAloudCueSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsVoiceCatalog
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.ui.book.readaloud.cache.TtsCacheViewModel
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingViewModel
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsViewModel
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerCoordinator
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.domain.usecase.WritingWorkspaceIndexBuilder
import io.legado.app.domain.usecase.AdoptDerivedToBookUseCase
import io.legado.app.domain.usecase.GenerateBookCanonicalUseCase
import io.legado.app.domain.usecase.structured.CharacterCardMutator
import io.legado.app.domain.usecase.structured.UserCardMutator
import io.legado.app.domain.usecase.structured.MemoryTableMutator
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.usecase.structured.OutlineBranchChoiceUseCase
import io.legado.app.domain.usecase.structured.OutlineMutator
import io.legado.app.domain.usecase.structured.StructuredDataValidator
import io.legado.app.domain.usecase.structured.StructuredDataPreviewer
import io.legado.app.domain.usecase.structured.WorldBookMutator
import io.legado.app.data.repository.AiPromptPipelineRepository
import io.legado.app.data.repository.AiWorldBookEntryRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.domain.gateway.AiPromptPipelineGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.prompt.ContextBudgeter
import io.legado.app.domain.prompt.PromptAssembler
import io.legado.app.domain.prompt.PromptPresetExporter
import io.legado.app.domain.prompt.PromptPresetImporter
import io.legado.app.domain.prompt.WorldEntryScanner
import io.legado.app.domain.usecase.GenerationOrchestrator
import io.legado.app.domain.usecase.ToolAgentLoop
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.ui.about.CrashViewModel
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.ui.ai.table.AiMemoryTableViewModel
import io.legado.app.ui.ai.worldbook.AiWorldBookViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import io.legado.app.ui.file.FileManageViewModel
import io.legado.app.ui.book.source.debug.BookSourceDebugViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.manga.MangaReaderViewModel
import io.legado.app.data.repository.manga.DefaultMangaReaderSession
import io.legado.app.data.repository.manga.MangaReaderActionRepository
import io.legado.app.data.repository.manga.MangaReaderDataRepository
import io.legado.app.domain.gateway.MangaReaderDataGateway
import io.legado.app.domain.gateway.MangaReaderSessionFactory
import io.legado.app.ui.book.source.manage.BookSourceViewModel
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtTocRuleViewModel
import io.legado.app.ui.config.ai.AiConfigViewModel
import io.legado.app.ui.config.ai.AiAbilityManagementViewModel
import io.legado.app.ui.config.ai.AiSkillsViewModel
import io.legado.app.ui.config.ai.AiSkillEditViewModel
import io.legado.app.ui.config.ai.PromptPipelineViewModel
import io.legado.app.ui.config.ai.PromptTemplateViewModel
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.dict.rule.DictRuleViewModel
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.rss.RssViewModel
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.rss.source.debug.RssSourceDebugViewModel
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import io.legado.app.ui.rss.source.manage.RssSourceViewModel
import io.legado.app.data.repository.RssSourceRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.MangaSettingsRepository
import io.legado.app.data.repository.OtherSettingsRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.BackupSettingsRepository
import io.legado.app.data.repository.DownloadCacheSettingsRepository
import io.legado.app.data.repository.WebDavReadingProgressRepository
import kotlinx.coroutines.Dispatchers
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
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
    viewModelOf(::AllBookmarkViewModel)
    viewModelOf(::FileManageViewModel)

    // Toc (chapter list / bookmarks, shared by TocActivity shell and main-stack TocEntry)
    viewModelOf(::TocViewModel)

    // Rule management pages
    viewModelOf(::TxtTocRuleViewModel)
    viewModelOf(::DictRuleViewModel)
    viewModelOf(::ReplaceRuleViewModel)

    // Book source management pages
    singleOf(::BookSourceRepository)
    viewModelOf(::BookSourceViewModel)
    viewModelOf(::BookSourceEditViewModel)
    viewModelOf(::BookSourceDebugViewModel)

    // RSS source management pages
    singleOf(::RssSourceRepository)
    viewModelOf(::RssSourceViewModel)
    viewModelOf(::RssSourceEditViewModel)
    viewModelOf(::RssSourceDebugViewModel)

    // ReplaceEdit (keyed by route session)
    viewModel { (route: ReplaceEditRoute) ->
        ReplaceEditViewModel(get(), get(), route)
    }

    // Upload
    single<UploadRepository> { DirectLinkUploadRepository() }

    // Manga reader settings backend (from legado-with-MD3)
    singleOf(::SettingsRepository)
    single<OtherSettingsGateway> { OtherSettingsRepository() }
    single<DownloadCacheSettingsGateway> { DownloadCacheSettingsRepository() }
    single<BackupSettingsGateway> { BackupSettingsRepository() }
    single<MangaSettingsGateway> { MangaSettingsRepository() }
    single { ReadSettingsRepository() }
    single<ReadSettingsGateway> { get<ReadSettingsRepository>() }
    single<ReadingProgressGateway> { WebDavReadingProgressRepository() }
    singleOf(::GetReadingProgressUseCase)
    singleOf(::UploadReadingProgressUseCase)
    // Coil ImageLoader 复用 CoilInitializer 配置的全局单例
    single<ImageLoader> { SingletonImageLoader.get(get()) }
    factoryOf(::MangaReaderDataRepository)
    factoryOf(::MangaReaderActionRepository)
    factory<MangaReaderDataGateway> { get<MangaReaderDataRepository>() }
    factory<MangaReaderSessionFactory> {
        MangaReaderSessionFactory {
            DefaultMangaReaderSession(
                dataGateway = get<MangaReaderDataGateway>(),
                stateDispatcher = Dispatchers.Default,
                ioDispatcher = Dispatchers.IO,
            )
        }
    }
    viewModelOf(::MangaReaderViewModel)

    // AI Gateways
    single<AiProfileGateway> { AiProfileRepository(get()) }
    single<AiArtifactGateway> { AiArtifactRepository(get()) }
    single<AiMemoryGateway> { AiMemoryRepository(get()) }
    single<AiMemoryTableGateway> { AiMemoryTableRepository(get(), get()) }
    single<AiOutlineGateway> { AiOutlineRepository(get()) }
    single<AiPlanGateway> { AiPlanRepository(get()) }
    single<AiTodoGateway> { AiTodoRepository(get()) }
    single<AiBookOutlineGateway> { AiBookOutlineRepository(get()) }
    single<AiHtmlAppGateway> { AiHtmlAppRepository(get()) }
    single<AiWorkspaceGateway> { AiWorkspaceRepository(get(), get()) }
    single<AiChatGateway> { AiChatRepository(get(), get(), get(), get(), get()) }
    single { AiTextRepositoryImpl() }
    single { AiUsageRepository() }
    single<AiTextGateway> {
        CacheFirstAiTextGateway(
            delegate = MeteringAiTextGateway(get<AiTextRepositoryImpl>(), get()),
            artifactGateway = get(),
        )
    }
    singleOf(::AiEvalJsUseCase)
    single<AiSkillGateway> { AiSkillRepository(get()) }
    single<AiToolGateway> {
        AiToolRepository(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(),
        )
    }
    single<AiToolConfigGateway> { AiToolConfigRepository(get()) }
    single<AiPromptTemplateGateway> { AiPromptTemplateRepository(get()) }
    single<AiCharacterCardGateway> { AiCharacterCardRepository(get()) }
    singleOf(::CloudTtsCredentialCipher)
    single<ReadAloudCharacterGateway> { CharacterCardSpeakerRepository(get(), get()) }
    single<ReadAloudVoiceGateway> { ReadAloudVoiceRepository(get()) }
    single<ChapterSpeechGateway> { ChapterSpeechRepository(get()) }
    single<CloudTtsEngineGateway> { CloudTtsEngineRepository(get(), get()) }
    singleOf(::ReadAloudSettingsRepository)
    single<ReadAloudSettingsGateway> { get<ReadAloudSettingsRepository>() }
    single<HttpTtsEngineGateway> { HttpTtsEngineRepository(get()) }
    singleOf(::ReadAloudSessionStore)

    singleOf(::AnalyzeChapterSpeechUseCase)
    singleOf(::ResolveLocalSpeakersUseCase)
    singleOf(::BuildSpeechPlanUseCase)
    singleOf(::PrepareChapterSpeechPlanUseCase)
    singleOf(::SyncReadAloudVoicesUseCase)
    singleOf(::ExportCloudTtsAsHttpTtsUseCase)
    singleOf(::RefineSpeechWithAiUseCase)
    singleOf(::IdentifyBookCharactersUseCase)

    singleOf(::CloudTtsAudioSynthesizer)
    single { SystemTtsFileSynthesizer(androidContext()) }
    single { SystemTtsVoiceCatalog(androidContext()) }
    single { HttpTtsStreamFetcher() }
    single { ReadAloudCueSynthesizer(androidContext(), get(), get(), get()) }
    singleOf(::SyncVoicesFromAllEnginesUseCase)
    singleOf(::TtsConfigTools)
    singleOf(::SkillTools)
    singleOf(::PlanFileTools)
    singleOf(::TodoTools)
    singleOf(::HtmlAppTools)

    singleOf(::ReadAloudPlayerCoordinator)
    viewModelOf(::ReadAloudPlayerViewModel)
    viewModelOf(::CloudTtsViewModel)
    viewModelOf(::TtsCacheViewModel)
    viewModel { (bookUrl: String) ->
        BookVoiceCastingViewModel(bookUrl, get(), get(), get(), get())
    }
    viewModel { (bookUrl: String) ->
        io.legado.app.ui.book.info.characters.BookCharacterListViewModel(bookUrl, get(), get(), get())
    }
    viewModel { (bookUrl: String, focusCharacterId: String?) ->
        io.legado.app.ui.book.info.network.BookCharacterNetworkViewModel(
            bookUrl, get(), get(), get(), focusCharacterId,
        )
    }

    single<AiWritingPromptGateway> { AiWritingPromptRepository(get()) }
    single<AiWorldBookGateway> { AiWorldBookRepository(get()) }
    single<AiPromptPipelineGateway> { AiPromptPipelineRepository(get()) }
    single<AiWorldBookEntryGateway> { AiWorldBookEntryRepository(get()) }

    // AI Use Cases
    singleOf(::ContextBudgeter)
    singleOf(::WorldEntryScanner)
    singleOf(::PromptAssembler)
    singleOf(::PromptPresetExporter)
    singleOf(::PromptPresetImporter)
    singleOf(::ToolAgentLoop)
    singleOf(::GenerationOrchestrator)
    singleOf(::AiChatGenerationUseCase)
    singleOf(::ResolveBookChapterContentUseCase)
    singleOf(::SearchBookSourcesUseCase)
    singleOf(::SearchWorkspaceUseCase)
    singleOf(::SearchBookContentUseCase)
    singleOf(::AddBookToBookshelfUseCase)
    singleOf(::BookshelfAccessPreviewer)
    singleOf(::WebSearchRateLimiter)
    singleOf(::WebSearchUseCase)
    singleOf(::FetchWebPageUseCase)
    singleOf(::ExtractWorldBookUseCase)
    singleOf(::ImportWorldInfoUseCase)
    singleOf(::StructuredDataPreviewer)
    singleOf(::MemoryTableMutator)
    singleOf(::OutlineMutator)
    singleOf(::OutlineBranchChoiceUseCase)
    singleOf(::CharacterCardMutator)
    singleOf(::UserCardMutator)
    singleOf(::WorldBookMutator)
    singleOf(::StructuredDataValidator)
    singleOf(::MutationSnapshotService)
    singleOf(::BookSourceVersionService)
    singleOf(::CheckBookSourceUseCase)
    singleOf(::SearchRuleHelpUseCase)
    singleOf(::WriteBookSourceFromUrlUseCase)
    singleOf(::BookSourceAgentTools)
    singleOf(::WritingWorkspaceIndexBuilder)
    singleOf(::WritingStructuredMaintainUseCase)
    singleOf(::AdoptDerivedToBookUseCase)
    singleOf(::GenerateBookCanonicalUseCase)

    // AI ViewModels
    viewModelOf(::AiConfigViewModel)
    viewModel {
        AiChatViewModel(
            aiChatGateway = get(),
            aiProfileGateway = get(),
            aiCharacterCardGateway = get(),
            aiWritingPromptGateway = get(),
            aiSkillGateway = get(),
            aiWorldBookGateway = get(),
            aiMemoryTableGateway = get(),
            aiMemoryGateway = get(),
            promptTemplateGateway = get(),
            generationUseCase = get(),
            aiOutlineGateway = get(),
            aiToolConfigGateway = get(),
            aiTextGateway = get(),
            aiToolGateway = get(),
            outlineMutator = get(),
            outlineBranchChoiceUseCase = get(),
            characterCardMutator = get(),
            readAloudCharacterGateway = get(),
            structuredDataValidator = get(),
            mutationSnapshotService = get(),
            aiWorkspaceGateway = get(),
            workspaceIndexBuilder = get(),
            structuredMaintainUseCase = get(),
            promptAssembler = get(),
            promptPipelineGateway = get(),
            generationOrchestrator = get(),
            bookshelfAccessPreviewer = get(),
            importWorldInfoUseCase = get(),
            adoptUseCase = get(),
            aiPlanGateway = get(),
            aiTodoGateway = get(),
            aiHtmlAppGateway = get(),
        )
    }
    viewModelOf(::AiWorldBookViewModel)
    viewModelOf(::AiAbilityManagementViewModel)
    viewModelOf(::AiSkillsViewModel)
    viewModel { (skillId: String) ->
        AiSkillEditViewModel(skillId = skillId, skillGateway = get())
    }
    viewModelOf(::AiMemoryTableViewModel)
    viewModelOf(::PromptTemplateViewModel)
    viewModelOf(::PromptPipelineViewModel)
}
