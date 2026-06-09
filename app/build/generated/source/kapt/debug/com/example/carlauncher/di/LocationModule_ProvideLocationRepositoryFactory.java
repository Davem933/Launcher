package com.example.carlauncher.di;

import android.content.Context;
import com.example.carlauncher.data.location.LocationProcessor;
import com.example.carlauncher.data.location.LocationRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class LocationModule_ProvideLocationRepositoryFactory implements Factory<LocationRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<FusedLocationProviderClient> fusedClientProvider;

  private final Provider<LocationProcessor> processorProvider;

  public LocationModule_ProvideLocationRepositoryFactory(Provider<Context> contextProvider,
      Provider<FusedLocationProviderClient> fusedClientProvider,
      Provider<LocationProcessor> processorProvider) {
    this.contextProvider = contextProvider;
    this.fusedClientProvider = fusedClientProvider;
    this.processorProvider = processorProvider;
  }

  @Override
  public LocationRepository get() {
    return provideLocationRepository(contextProvider.get(), fusedClientProvider.get(), processorProvider.get());
  }

  public static LocationModule_ProvideLocationRepositoryFactory create(
      Provider<Context> contextProvider, Provider<FusedLocationProviderClient> fusedClientProvider,
      Provider<LocationProcessor> processorProvider) {
    return new LocationModule_ProvideLocationRepositoryFactory(contextProvider, fusedClientProvider, processorProvider);
  }

  public static LocationRepository provideLocationRepository(Context context,
      FusedLocationProviderClient fusedClient, LocationProcessor processor) {
    return Preconditions.checkNotNullFromProvides(LocationModule.INSTANCE.provideLocationRepository(context, fusedClient, processor));
  }
}
