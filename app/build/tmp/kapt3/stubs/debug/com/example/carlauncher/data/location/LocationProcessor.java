package com.example.carlauncher.data.location;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0007\b\u0007\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bJ\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/example/carlauncher/data/location/LocationProcessor;", "", "()V", "kalmanFilter", "Lcom/example/carlauncher/data/location/KalmanFilter;", "speedBuffer", "Lkotlin/collections/ArrayDeque;", "", "process", "Lcom/example/carlauncher/data/model/VehicleDisplayLocation;", "location", "Landroid/location/Location;", "reset", "", "app_debug"})
public final class LocationProcessor {
    @org.jetbrains.annotations.NotNull()
    private final com.example.carlauncher.data.location.KalmanFilter kalmanFilter = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.collections.ArrayDeque<java.lang.Float> speedBuffer = null;
    
    @javax.inject.Inject()
    public LocationProcessor() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.example.carlauncher.data.model.VehicleDisplayLocation process(@org.jetbrains.annotations.NotNull()
    android.location.Location location) {
        return null;
    }
    
    public final void reset() {
    }
}