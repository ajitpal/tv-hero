"""Every screenshot shown on the website and in the README, with a title, a description and tags.

One list feeds the website gallery (tools/build_site.py), the README gallery (tools/readme_gallery.py) and the
image sitemap. Screens come from tests with made-up sample data, never from a real family's TV:
  TV:    ./gradlew :tv-launcher:testDebugUnitTest -Pscreenshots=record   -> docs/screenshots/tv/
  phone: ./gradlew :companion:testDebugUnitTest -Pscreenshots=record     -> docs/screenshots/phone/
(01-home, 02-watch-screensaver and 03-times-up on the TV are photos of the reference TV.)
"""

# Filters, in the order shown: (tag, label).
TAGS = [
    ("kids", "For kids"),
    ("parents", "For parents"),
    ("limits", "Time & bedtime"),
    ("safety", "Safety"),
    ("tools", "Tools"),
    ("setup", "Set up"),
]

# (device, file, title, description, tags)
SHOTS = [
    ("tv", "01-home.png", "A calm Home screen",
     "tv-hero's Home screen on an Android TV: the clock, a greeting and only the apps for this profile. No ads, no autoplay rows.", ["parents"]),
    ("tv", "04-whos-watching.png", "Who's watching?",
     "Each child picks their own profile with the remote. Guest and Parent are there too, and a Parent Menu.", ["kids", "parents"]),
    ("tv", "05-times-up-earn-time.png", "Time's up, with Earn Time",
     "When the day's TV time is used up the video stops and the app closes. Kids can earn more minutes with questions, or ask a parent.", ["kids", "limits"]),
    ("tv", "06-earn-time-question.png", "An Earn Time question",
     "Maths, English words, general knowledge, Hindi and puzzles at Easy, Medium or Hard, answered with the remote.", ["kids"]),
    ("tv", "07-earn-time-correct.png", "Right answer: five more minutes",
     "Every right answer adds minutes of TV, up to a daily maximum the parent chooses.", ["kids"]),
    ("tv", "08-earn-time-not-quite.png", "Not quite: no minutes lost",
     "A wrong answer costs nothing; three in a row means a short break, so guessing doesn't pay.", ["kids"]),
    ("tv", "03-times-up.png", "Every bonus minute earned",
     "After the daily maximum of bonus minutes the TV says well done and stays off until a parent adds time.", ["kids", "limits"]),
    ("tv", "09-bedtime.png", "Bedtime",
     "At bedtime a good-night screen appears and the TV goes to standby a minute later. One more hour needs a parent.", ["limits"]),
    ("tv", "10-paused.png", "Paused from the phone",
     "Dinner time: a parent pauses the whole TV from their phone. Only the Parent PIN unlocks it on the TV.", ["parents", "limits"]),
    ("tv", "11-app-waiting-for-approval.png", "New apps wait for approval",
     "Anything newly installed stays blocked until a parent approves it in the phone app.", ["safety"]),
    ("tv", "12-parents-only.png", "Settings are for parents",
     "In a child's profile, Settings and app installs are parent-only. A parent can switch to Parent right there with the PIN.", ["safety"]),
    ("tv", "13-parent-menu.png", "Parent Menu on the TV",
     "Profiles, apps for everyone, the start-up profile, PIN, paired phones, guest time and switch limits, right on the TV.", ["parents"]),
    ("tv", "14-parent-pin.png", "Parent PIN",
     "A number pad for the remote. Loosening a rule always needs the parent PIN or password; tightening never does.", ["parents", "safety"]),
    ("tv", "15-pairing-code.png", "Pairing: compare the code",
     "A new phone shows a six-digit code; the TV shows the same code and someone in the room presses Allow.", ["safety", "setup"]),
    ("tv", "16-tools.png", "Tools",
     "Timer, Stopwatch, Workout and a clock, built into the TV and controllable from the phone.", ["tools"]),
    ("tv", "17-timer.png", "Timer",
     "Big digits and a ring that empties as time runs out, readable from across the room.", ["tools"]),
    ("tv", "18-workout-tabata.png", "Workout: Tabata",
     "HIIT presets like Tabata: each phase fills the screen in its own colour, with beeps, rounds and a bar for the whole workout.", ["tools"]),
    ("tv", "19-corner-timer-over-video.png", "Corner timer over any app",
     "Start a workout, open a video: a small timer stays in the corner over the app.", ["tools"]),
    ("tv", "02-watch-screensaver.png", "Watch screensaver",
     "An analogue watch and the date on a gradient that follows the time of day. It never counts as screen time.", ["tools"]),

    ("phone", "17-welcome.png", "Welcome",
     "The phone app's first screen: set up a new TV, or connect to one that already runs tv-hero.", ["setup"]),
    ("phone", "18-set-up-new-tv.png", "Set Up a New TV",
     "The phone finds the TV on the home Wi-Fi, installs tv-hero and sets it up. No computer, no cables.", ["setup"]),
    ("phone", "13-pair.png", "Pair this phone",
     "Pairing asks the TV to show a code; a person compares it and presses Allow on the TV.", ["setup", "safety"]),
    ("phone", "01-tv.png", "What's on, and what needs you",
     "Who's watching, what's playing and how much time is left, with kids' requests under Needs You.", ["parents"]),
    ("phone", "02-family.png", "Family: profiles and Guest",
     "Every profile with today's time and bedtime at a glance, plus Guest and the start-up profile.", ["parents"]),
    ("phone", "04-kid-profile.png", "A child's rules",
     "Daily limit, Time Today (give or take minutes, End Now), bedtime and Earn Time for one child.", ["parents", "limits"]),
    ("phone", "03-family-profile.png", "End today's TV now",
     "A profile's screen time today, its daily limit and End Today's Time Now.", ["limits"]),
    ("phone", "16-guest.png", "Guest Time",
     "Guests get their own daily allowance, shared by everyone who uses Guest that day.", ["limits"]),
    ("phone", "06-apps.png", "Approve and block apps",
     "New apps wait here for approval; any app can be blocked for everyone.", ["safety", "parents"]),
    ("phone", "05-apps-for-everyone.png", "Apps for Everyone",
     "Apps every profile gets on top of its own list.", ["parents"]),
    ("phone", "07-remote.png", "Remote and game controller",
     "A full remote, typing on the TV and the phone as a game controller.", ["parents"]),
    ("phone", "12-night-volume.png", "Night volume cap",
     "Between set times the TV's volume can't go above a cap.", ["limits"]),
    ("phone", "10-who-can-control.png", "Who can control this TV",
     "Every paired phone, when it was last used and from where. Removing one takes effect at once.", ["safety"]),
    ("phone", "11-internet-control.png", "Internet control, off by default",
     "Control from outside the home stays off until a parent turns it on at home, with Allow on the TV.", ["safety"]),
    ("phone", "09-home-screen.png", "Arrange the Home screen",
     "Order the app row, apps per row and the greeting shown on the TV.", ["parents"]),
    ("phone", "08-settings.png", "Settings",
     "The TV, its Home screen, night volume, security and this phone's app lock.", ["parents", "safety"]),
    ("phone", "14-tv-dark.png", "Dark mode",
     "The phone app follows the phone's light or dark mode.", ["parents"]),
    ("phone", "15-family-dark.png", "Family, in dark mode",
     "Profiles and Guest in dark mode.", ["parents"]),
]


def label(tag):
    return dict(TAGS).get(tag, tag)
