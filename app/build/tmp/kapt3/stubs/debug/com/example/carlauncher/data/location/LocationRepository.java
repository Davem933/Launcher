package com.example.carlauncher.data.location;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B!\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0006\u0010\u0014\u001a\u00020\u0015J\u0006\u0010\u0016\u001a\u00020\u0015R\u0016\u0010\t\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u0010\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000b0\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006\u0017"}, d2 = {"Lcom/example/carlauncher/data/location/LocationRepository;", "", "context", "Landroid/content/Context;", "fusedClient", "Lcom/google/android/gms/location/FusedLocationProviderClient;", "processor", "Lcom/example/carlauncher/data/location/LocationProcessor;", "(Landroid/content/Context;Lcom/google/android/gms/location/FusedLocationProviderClient;Lcom/example/carlauncher/data/location/LocationProcessor;)V", "_vehicleLocation", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/example/carlauncher/data/model/VehicleDisplayLocation;", "locationCallback", "Lcom/google/android/gms/location/LocationCallback;", "locationRequest", "Lcom/google/android/gms/location/LocationRequest;", "vehicleLocation", "Lkotlinx/coroutines/flow/StateFlow;", "getVehicleLocation", "()Lkotlinx/coroutines/flow/StateFlow;", "startTracking", "", "stopTracking", "app_debug"})
public final class LocationRepository {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.google.android.gms.location.FusedLocationProviderClient fusedClient = null;
    @org.jetbrains.annotations.NotNull()
    private final com.example.carlauncher.data.location.LocationProcessor processor = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.example.carlauncher.data.model.VehicleDisplayLocation> _vehicleLocation = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.example.carlauncher.data.model.VehicleDisplayLocation> vehicleLocation = null;
    @org.jetbrains.annotations.NotNull()
    private final com.google.android.gms.location.LocationRequest locationRequest = null;
    @org.jetbrains.annotations.NotNull()
    private final com.google.android.gms.location.LocationCallback locationCallback = null;
    
    @javax.inject.Inject()
    public LocationRepository(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.google.android.gms.location.FusedLocationProviderClient fusedClient, @org.jetbrains.annotations.NotNull()
    com.example.carlauncher.data.location.LocationProcessor processor) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.example.carlauncher.data.model.VehicleDisplayLocation> getVehicleLocation() {
        return null;
    }
    
    public final void startTracking() {
    }
    
    public final void stopTracking() {
    }
}