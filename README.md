# FreeWheel - Android Bike Tracking App

A comprehensive Android application for tracking bike rides with GPS precision and heart rate monitoring, similar to Strava functionality. Open source code with no logins, no ads, and no trackers. Data is stored locally on your device, with future plans to provide optional cloud storage integration. 

## Features

### Core Tracking
- **GPS Tracking**: High-precision location tracking during bike rides with professional speed filtering
- **Real-time Statistics**: Current speed, distance, duration, and elevation with live updates
- **Heart Rate Monitoring**: BLE integration with Polar H10 and compatible heart rate monitors
- **Route Recording**: Save and visualize your bike routes with detailed GPS tracks
- **Auto-pause/Resume**: Intelligent detection of stops with automatic pause and resume functionality
- **Advanced Speed Processing**: Multi-stage filtering system for accurate speed measurements

### Data & Analytics
- **Ride History**: Complete log of all your bike rides with detailed statistics
- **Performance Metrics**: Average/max speed and heart rate analysis
- **Elevation Tracking**: Gain/loss calculations with climb and descent event detection
- **Event Logging**: Automatic detection of pauses, stops, sprints, and elevation changes
- **Moving Time Calculation**: Accurate moving time excluding pauses and stops
- **Statistics Dashboard**: Comprehensive ride performance overview

### User Interface
- **Real-time Dashboard**: Live tracking interface with key metrics and ride controls
- **Interactive Maps**: OpenStreetMap integration with route visualization and current location
- **History View**: Browse and analyze past rides with detailed statistics
- **Material Design**: Modern Android UI with Material 3 components and bike-themed colors
- **Dark Mode Support**: Proper theming for both light and dark mode preferences
- **Finish Ride Dialog**: Save or discard ride confirmation with detailed statistics

## Technical Architecture

### Tech Stack
- **Language**: Kotlin
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite)
- **Maps**: OpenStreetMap (OSMDroid)
- **Location**: Google Location Services
- **Bluetooth**: Nordic BLE library for heart rate monitoring
- **UI**: Material Design Components, ViewBinding, Navigation Component

### Key Components
- **Location Tracking Service**: Background GPS tracking with high accuracy and power optimization
- **Heart Rate Service**: BLE connection management for Polar H10 and compatible heart rate monitors
- **Database Layer**: Room database with comprehensive data models and relationships
- **Repository Pattern**: Clean data access abstraction with coroutines support
- **ViewModels**: UI state management and business logic with proper lifecycle handling
- **Professional Speed Filtering**: Multi-stage filtering including median filtering, EMA smoothing, and stop detection
- **Advanced GPS Processing**: Accuracy filtering, jump detection, and position-based speed calculation

## Setup Requirements

### Prerequisites
1. **Android Studio**: Latest version with Kotlin support
2. **Android SDK**: API level 24+ (Android 7.0+)
3. **Physical Device**: Recommended for GPS and BLE testing

### Permissions Required
- `ACCESS_FINE_LOCATION`: GPS tracking
- `ACCESS_COARSE_LOCATION`: Location services
- `BLUETOOTH` & `BLUETOOTH_ADMIN`: BLE communication
- `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN`: Android 12+ BLE permissions
- `FOREGROUND_SERVICE`: Background tracking
- `FOREGROUND_SERVICE_LOCATION`: Location-based foreground service

### Installation Steps
1. Clone the repository: `git clone https://github.com/crmadsen/FreeWheel.git`
2. Open in Android Studio
3. Sync project with Gradle files
4. Build and run on a physical Android device (recommended for GPS accuracy)
5. Grant location permissions when prompted for optimal tracking experience

## Usage

### Starting a Ride
1. Open the app and navigate to the "Tracking" tab
2. Grant location and Bluetooth permissions when prompted
3. Optionally connect your Polar H10 heart rate monitor
4. Tap "Start Ride" to begin tracking
5. Monitor real-time statistics during your ride
6. Tap "Stop Ride" when finished

### Viewing Routes
1. Navigate to the "Map" tab during or after a ride
2. View your current location and route path
3. Use the location button to center on your current position

### Ride History
1. Navigate to the "History" tab
2. Browse all completed rides
3. View detailed statistics for each ride
4. Analyze performance trends over time

## Development Status

### Completed Features
- ✅ Project structure and dependencies
- ✅ Database schema (Ride, LocationPoint, HeartRateData)
- ✅ Repository pattern implementation
- ✅ Basic UI layouts and navigation
- ✅ Tracking fragment with real-time display
- ✅ Map fragment for route visualization
- ✅ History fragment for ride logs

### Upcoming Features
- 🔄 Location tracking service implementation
- 🔄 BLE heart rate monitor integration
- 🔄 OpenStreetMap route visualization
- 🔄 Statistics calculation algorithms
- 🔄 Background service optimization
- 🔄 Data export functionality
- 🔄 Cloud drive upload functionality
- 🔄 Advanced charts and analytics

## Contributing

This project follows standard Android development practices:
- MVVM architecture pattern
- Repository pattern for data access
- Coroutines for asynchronous operations
- Material Design guidelines
- Room database for persistence

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, feature requests, or questions about the bike tracking app, please create an issue in the repository.
