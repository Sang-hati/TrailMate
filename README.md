TrailMate
TrailMate is an Android hiking journal. It can record your hikes with GPS, counts your steps, and keeps a history of every trail, with a map, photo and notes for each one.
Built with Kotlin, Room (SQLite), osmdroid (OpenStreetMap) and Google's Fused Location Provider. No Google Maps API key is used.
________________________________________
Features
•	Record a hike with live distance, time and step count, drawn on a map as you walk
•	Pause, resume, stop or discard a recording at any time
•	Keeps recording in the background with an ongoing notification (lock the phone, switch apps, or press back)
•	Recording stops when app is removed from recent apps.
•	Daily steps ring with an adjustable goal.
•	Step history charts for the week, month, 3 months and year
•	Trail journal with a route preview on every card, place names, notes and a photo
•	Share a hike as a picture card, or export it as a GPX file
•	Stats screen with total distance, total time, longest hike and more
•	Day and night themes, and portrait and landscape layouts
________________________________________
STEPS TO NAVIGATE THE APP :
1. Install and open
 Maps need an internet connection to load. Place names also need internet.
2. Allow permissions
When asked, allow:
Permission	Why it's needed
Location	Draws your route and measures distance
Physical activity	Counts your steps
Notifications	Shows the recording notification
The camera permission isn't needed. TrailMate uses the phone’s camera app to take photos.

3. How to use the app
A. Home screen ("My Hikes")
•	The steps ring shows today's steps against your goal. Tap it to change your goal.
•	Recent trails shows your latest 3 hikes. Tap a card to open it.
•	See all trails shows every hike you've ever saved.
•	View my stats opens the Stats screen.

B. Recording a hike
a.	On the Home screen, open Record Hike ( the ADD symbol at the bottom left ).
b.	Type a hike name (at least 3 characters). You can't start without one.
c.	Optionally change the date (future dates aren't allowed), add notes, or tap Add photo to take a picture.
d.	Tap Start. Make sure GPS is turned on. The first point appears once the GPS has a signal.
e.	Walk. The map follows you, and the top shows distance, time and steps.
f.	Use the buttons as you go: 
o	Pause / Resume: stops the timer, GPS and step count, then continues later. The walk between pause and resume isn't counted.
o	Stop: ends the recording but keeps the route, so you can still save it.
o	Save hike: saves the hike. You can tap this mid-hike and it will stop and save for you. You need at least 10 metres recorded.
o	Discard: asks for confirmation, then throws the hike away.

C. Recording in the background
•	While you record, an ongoing "Recording hike" notification shows live distance, time and steps.
•	The Pause/Resume and Stop buttons on the notification work from the lock screen.
•	Pressing back or leaving the app does not stop the recording. Tap the notification to return to the Record screen.
•	Swiping the app away from Recents stops and discards the recording.

D. Viewing a past hike
Tap any trail card to see:
•	A map of the route with start and end markers
•	Date, distance, time and steps, plus the start and end places
•	Your notes, with Add notes / Edit notes (up to 300 characters)
•	Your photo. Tap it to open it in your gallery. Saved photos are also copied to the Pictures/TrailMate album.
•	Share card: creates a picture of the route and stats to send to WhatsApp, Instagram and others
•	Export GPX: shares the route as a .gpx file for apps like Google Earth.
•	Delete hike: asks for confirmation first

E. Stats screen
•	Total hikes, total distance, total time, average hike, longest hike and average speed
•	Step history chart. Switch between Week, Month, 3 months and Year, and tap a bar to see that day's steps. The dashed line is your daily goal.
•	Load demo data fills the charts with sample history so you can see the longer ranges. Remove demo data clears only the sample data. ( Given for testing purposes )

________________________________________
Tech stack
•	Language: Kotlin, single-activity app with Fragments and the Navigation component
•	UI: Material 3, custom views for the route preview and bar chart, day and night themes
•	Storage: Room (SQLite) with migrations. Tables: hikes, daily_steps
•	Maps and location: osmdroid (OpenStreetMap), Fused Location Provider, Geocoder
•	Background work: foreground service for recording, WorkManager for daily step logging
•	Other: step counter sensor, camera via FileProvider, MediaStore, notifications
________________________________________
Sanghati Debnath ( CMSA - 546 – UG4)


