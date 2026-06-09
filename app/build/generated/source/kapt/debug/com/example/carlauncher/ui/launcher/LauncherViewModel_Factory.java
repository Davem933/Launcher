package com.example.carlauncher.ui.launcher;

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
public final class LauncherViewModel_Factory implements Factory<LauncherViewModel> {
  private final Provider<LocationRepository> repositoryProvider;

  public LauncherViewModel_Factory(Provider<LocationRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public LauncherViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static LauncherViewModel_Factory create(Provider<LocationRepository> repositoryProvider) {
    return new LauncherViewModel_Factory(repositoryProvider);
  }

  public static LauncherViewModel newInstance(LocationRepository repository) {
    return new LauncherViewModel(repository);
  }
}
