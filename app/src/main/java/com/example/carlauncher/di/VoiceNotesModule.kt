package com.example.carlauncher.di

import android.content.Context
import androidx.room.Room
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.notes.GroqClient
import com.example.carlauncher.data.notes.NoteAi
import com.example.carlauncher.data.notes.VoiceNoteDao
import com.example.carlauncher.data.notes.VoiceNotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceNotesModule {

    @Provides
    @Singleton
    fun providesVoiceNotesDatabase(@ApplicationContext context: Context): VoiceNotesDatabase =
        Room.databaseBuilder(context, VoiceNotesDatabase::class.java, "voice_notes_database")
            .addMigrations(VoiceNotesDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providesVoiceNoteDao(db: VoiceNotesDatabase): VoiceNoteDao = db.voiceNoteDao()

    @Provides
    @Singleton
    fun providesNoteAi(): NoteAi = GroqClient(BuildConfig.GROQ_API_KEY)
}
