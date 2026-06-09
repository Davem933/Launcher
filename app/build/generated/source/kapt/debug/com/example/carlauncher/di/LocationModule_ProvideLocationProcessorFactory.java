package com.example.carlauncher.di;

import com.example.carlauncher.data.location.LocationProcessor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class LocationModule_ProvideLocationProcessorFactory implements Factory<LocationProcessor> {
  @Override
  public LocationProcessor get() {
    return provideLocationProcessor();
  }

  public static LocationModule_ProvideLocationProcessorFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LocationProcessor provideLocationProcessor() {
    return Preconditions.checkNotNullFromProvides(LocationModule.INSTANCE.provideLocationProcessor());
  }

  private static final class InstanceHolder {
    private static final LocationModule_ProvideLocationProcessorFactory INSTANCE = new LocationModule_ProvideLocationProcessorFactory();
  }
}
