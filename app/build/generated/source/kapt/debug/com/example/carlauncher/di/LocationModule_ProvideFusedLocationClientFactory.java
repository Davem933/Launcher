package com.example.carlauncher.di;

import android.content.Context;
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
public final class LocationModule_ProvideFusedLocationClientFactory implements Factory<FusedLocationProviderClient> {
  private final Provider<Context> contextProvider;

  public LocationModule_ProvideFusedLocationClientFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public FusedLocationProviderClient get() {
    return provideFusedLocationClient(contextProvider.get());
  }

  public static LocationModule_ProvideFusedLocationClientFactory create(
      Provider<Context> contextProvider) {
    return new LocationModule_ProvideFusedLocationClientFactory(contextProvider);
  }

  public static FusedLocationProviderClient provideFusedLocationClient(Context context) {
    return Preconditions.checkNotNullFromProvides(LocationModule.INSTANCE.provideFusedLocationClient(context));
  }
}
