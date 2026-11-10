# Embedded Weave Office

This module embeds the Android shell from:

- Project: `frozencaviar/Weave-Office-Android`
- Source commit: `96d9b5edab42923b38d77b3028d0b6f86c8b0266`
- Upstream release used for native engines: `android-v0.1.2`
- License: MIT

The module keeps the original `WeaveOfficeActivity` and its file bridge while
removing launcher registration, package-install permissions, and independent
application identity. Quyang Notes launches the Activity with an attachment
Content URI. Native engines are packaged only for `arm64-v8a`.

See `LICENSE-WEAVE-OFFICE.txt` and `NOTICE-WEAVE-OFFICE.txt`.
