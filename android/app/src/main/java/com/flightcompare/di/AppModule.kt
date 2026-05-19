package com.flightcompare.di

import android.content.Context
import androidx.room.Room
import com.flightcompare.data.local.FlightDatabase
import com.flightcompare.data.local.dao.FlightDao
import com.flightcompare.data.local.dao.OfferDao
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
    fun provideDatabase(@ApplicationContext context: Context): FlightDatabase {
        return Room.databaseBuilder(
            context,
            FlightDatabase::class.java,
            "flightcompare.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideOfferDao(db: FlightDatabase): OfferDao = db.offerDao()

    @Provides
    fun provideFlightDao(db: FlightDatabase): FlightDao = db.flightDao()
}
