package com.example.carlauncher.ui.map;

import com.example.carlauncher.data.location.LocationRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MapViewModel_Factory implements Factory<MapViewModel> {
  private final Provider<LocationRepository> repositoryProvider;

  public MapViewModel_Factory(Provider<LocationRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public MapViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static MapViewModel_Factory create(Provider<LocationRepository> repositoryProvider) {
    return new MapViewModel_Factory(repositoryProvider);
  }

  public static MapViewModel newInstance(LocationRepository repository) {
    return new MapViewModel(repository);
  }
}
