package com.example.carlauncher.ui.map;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u0006X\u0086D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0019\u0010\t\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000b0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u000e"}, d2 = {"Lcom/example/carlauncher/ui/map/MapViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/example/carlauncher/data/location/LocationRepository;", "(Lcom/example/carlauncher/data/location/LocationRepository;)V", "mapStyle", "", "getMapStyle", "()Ljava/lang/String;", "vehicleLocation", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/example/carlauncher/data/model/VehicleDisplayLocation;", "getVehicleLocation", "()Lkotlinx/coroutines/flow/StateFlow;", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class MapViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.example.carlauncher.data.model.VehicleDisplayLocation> vehicleLocation = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String mapStyle = "asset://style/map_style_dark.json";
    
    @javax.inject.Inject()
    public MapViewModel(@org.jetbrains.annotations.NotNull()
    com.example.carlauncher.data.location.LocationRepository repository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.example.carlauncher.data.model.VehicleDisplayLocation> getVehicleLocation() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getMapStyle() {
        return null;
    }
}