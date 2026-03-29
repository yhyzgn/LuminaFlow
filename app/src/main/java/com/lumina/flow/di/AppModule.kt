package com.lumina.flow.di

import android.content.Context
import androidx.room.Room
import com.lumina.flow.data.AutomationDao
import com.lumina.flow.data.AutomationDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutomationDatabase =
        Room.databaseBuilder(context, AutomationDatabase::class.java, "lumina_flow.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    @Singleton
    fun provideAutomationDao(database: AutomationDatabase): AutomationDao =
        database.automationDao()
}
