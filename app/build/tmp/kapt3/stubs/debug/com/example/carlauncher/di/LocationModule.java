package com.example.carlauncher.di;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u00020\u00042\b\b\u0001\u0010\u0005\u001a\u00020\u0006H\u0007J\b\u0010\u0007\u001a\u00020\bH\u0007J\"\u0010\t\u001a\u00020\n2\b\b\u0001\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\u00042\u0006\u0010\f\u001a\u00020\bH\u0007\u00a8\u0006\r"}, d2 = {"Lcom/example/carlauncher/di/LocationModule;", "", "()V", "provideFusedLocationClient", "Lcom/google/android/gms/location/FusedLocationProviderClient;", "context", "Landroid/content/Context;", "provideLocationProcessor", "Lcom/example/carlauncher/data/location/LocationProcessor;", "provideLocationRepository", "Lcom/example/carlauncher/data/location/LocationRepository;", "fusedClient", "processor", "app_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class LocationModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.example.carlauncher.di.LocationModule INSTANCE = null;
    
    private LocationModule() {
        super();
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final com.google.android.gms.location.FusedLocationProviderClient provideFusedLocationClient(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final com.example.carlauncher.data.location.LocationProcessor provideLocationProcessor() {
        return null;
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final com.example.carlauncher.data.location.LocationRepository provideLocationRepository(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.google.android.gms.location.FusedLocationProviderClient fusedClient, @org.jetbrains.annotations.NotNull()
    com.example.carlauncher.data.location.LocationProcessor processor) {
        return null;
    }
}