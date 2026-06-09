package com.example.carlauncher.data.location;

import android.content.Context;
import com.google.android.gms.location.FusedLocationProviderClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class LocationRepository_Factory implements Factory<LocationRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<FusedLocationProviderClient> fusedClientProvider;

  private final Provider<LocationProcessor> processorProvider;

  public LocationRepository_Factory(Provider<Context> contextProvider,
      Provider<FusedLocationProviderClient> fusedClientProvider,
      Provider<LocationProcessor> processorProvider) {
    this.contextProvider = contextProvider;
    this.fusedClientProvider = fusedClientProvider;
    this.processorProvider = processorProvider;
  }

  @Override
  public LocationRepository get() {
    return newInstance(contextProvider.get(), fusedClientProvider.get(), processorProvider.get());
  }

  public static LocationRepository_Factory create(Provider<Context> contextProvider,
      Provider<FusedLocationProviderClient> fusedClientProvider,
      Provider<LocationProcessor> processorProvider) {
    return new LocationRepository_Factory(contextProvider, fusedClientProvider, processorProvider);
  }

  public static LocationRepository newInstance(Context context,
      FusedLocationProviderClient fusedClient, LocationProcessor processor) {
    return new LocationRepository(context, fusedClient, processor);
  }
}
