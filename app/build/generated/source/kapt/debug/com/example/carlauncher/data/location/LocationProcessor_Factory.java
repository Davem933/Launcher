package com.example.carlauncher.data.location;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class LocationProcessor_Factory implements Factory<LocationProcessor> {
  @Override
  public LocationProcessor get() {
    return newInstance();
  }

  public static LocationProcessor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LocationProcessor newInstance() {
    return new LocationProcessor();
  }

  private static final class InstanceHolder {
    private static final LocationProcessor_Factory INSTANCE = new LocationProcessor_Factory();
  }
}
