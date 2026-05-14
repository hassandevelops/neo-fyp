package com.neo.di

import com.neo.domain.port.IImageCompressor
import com.neo.domain.port.ISyncPort
import com.neo.media.ImageCompressorAdapter
import com.neo.sync.GossipProtocol
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PortModule {

    @Binds
    abstract fun bindImageCompressor(
        imageCompressorAdapter: ImageCompressorAdapter
    ): IImageCompressor

    @Binds
    abstract fun bindSyncPort(
        gossipProtocol: GossipProtocol
    ): ISyncPort
}
