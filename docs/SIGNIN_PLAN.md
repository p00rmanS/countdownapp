# Sign-in and profiles: where this is going

**Today (built):** a local profile on web, Android and iPhone: name + photo (or Scott's face), shown on Home and in Settings,
saved in the device store and included in backups. No account, no server.

**Next: sign in with Google / Apple** so the profile follows a person between phones and, later, so two people can share countdowns live.

## What it needs
| Piece | Notes |
|---|---|
| OAuth client IDs | Google Cloud Console: one Web, one Android (package `app.pawcount` + SHA-1), one iOS client. Apple: Services ID + Sign in with Apple capability (required by App Store rules if any other social login is offered). **Only the app owner can create these.** |
| Client libraries | Web: Google Identity Services. Android: Credential Manager + `googleid`. iPhone: `AuthenticationServices` (Apple) and GoogleSignIn-iOS. |
| Backend | The first piece of server in the project. Smallest option: Supabase or Firebase Auth + one `profiles` table (id, display_name, avatar_url) and a storage bucket for avatars. The app sends the provider ID token, the backend verifies it and returns a session. |
| Profile fields | Pre-fill name and photo from the Google/Apple account; the user can override (the local profile screen already does this). |
| Privacy | Ask for `openid email profile` only. Store no tokens in backups. Add account deletion (App Store requirement). |

## Suggested order
1. Pick the backend (Supabase recommended: Postgres + Auth + Storage, free tier).
2. Add a `Session` object next to the store on each platform; a signed-out user keeps working exactly as today.
3. "Sign in" row in Settings > Profile; on success, upload the avatar and copy the display name.
4. Sync countdowns for signed-in users (last-write-wins per countdown id), then shared countdowns with live paw prints.
