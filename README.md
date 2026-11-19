# SpotCheck

SpotCheck helps students find nearby amenities on Grounds and compare ratings from other students. Each listing is a single facility, so two study rooms on different floors of the same library have separate reviews and averages. Built for CS 4720 (Fall 2026).

## Features

- Email/password and Google sign-in
- Explore screen with an interactive map and a list, search, category filters, and sorting by distance or rating
- Facility details with dated community reviews, plus write, edit, and delete for your own review (one per facility, anonymous)
- Share a facility recommendation through the Android share sheet
- Settings stored in DataStore: distance units, default category, default map or list view

## Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Firebase Authentication, Cloud Firestore, Credential Manager, Maps Compose, fused location, Preferences DataStore.

## Setup (for building from source)

1. Add your own `app/google-services.json` from a Firebase project with Email/Password and Google sign-in enabled. Register the SHA-1 of your signing key.
2. Add a Maps SDK for Android key to `local.properties`:
   ```
   MAPS_API_KEY=your_key
   ```
3. Deploy the security rules:
   ```
   npx firebase-tools deploy --only firestore:rules
   ```
4. Seed buildings and facilities through the Firebase Console. See `docs/seed-data.md`. Clients cannot write to the catalog.
5. Build and run:
   ```
   ./gradlew installDebug
   ```

Requires Android 8.1 (API 27) or newer.

## Data model

```
users/{uid}
buildings/{buildingId}
buildings/{buildingId}/facilities/{facilityId}
buildings/{buildingId}/facilities/{facilityId}/reviews/{uid}
```

Ratings are computed from reviews with Firestore aggregate queries. Rules let signed-in users read everything, and only the review author can write their own review.

## AI usage

Claude Code helped with parts of the implementation, the seed data scripts, and documentation. All code was reviewed by the author (me), and the architecture was also designed by me.
