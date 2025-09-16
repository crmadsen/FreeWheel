# Android Bike Tracking App - Copilot Instructions

This workspace contains an Android app for bike ride tracking with Strava-like functionality.

## Project Overview
- **Target Platform**: Android
- **Language**: Kotlin/Java
- **Key Features**: 
  - GPS tracking with high location accuracy
  - BLE heart rate monitor integration (Polar H10)
  - Real-time speed and distance tracking
  - Ride recording with time-series data
  - Statistics calculation (avg/max speed and heart rate)
  - Map view with route tracking
  - Current location display

## Development Guidelines
- Use modern Android development practices
- Implement proper permission handling for location and Bluetooth
- Follow Material Design guidelines
- Use Room database for local data persistence
- Implement proper lifecycle management for location services
- Handle BLE connection states gracefully
- Optimize for battery usage during long rides

## Key Dependencies
- Google Maps SDK
- Google Location Services
- Bluetooth LE APIs
- Room Database
- Kotlin Coroutines
- ViewBinding/DataBinding
- Navigation Component

## Architecture
- MVVM pattern with Repository pattern
- Single Activity with Fragments
- Background services for GPS and BLE
- Local database for ride data storage