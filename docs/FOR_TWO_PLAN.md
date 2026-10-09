# For two: shared countdowns that stay in sync

**What it is.** Two people share one countdown. Both phones show the same dog, the same date and the same checklist, and each of you
can see when the other one fed, watered or petted the dog today ("Mama gave Scott a treat · 10 min ago").

**What exists today.** A share link copies a countdown to the other person's phone once. After that the two copies drift apart.
Nothing is shared live, so there is no server in the project yet.

## What it needs (and why)
| Piece | Choice | Notes |
|---|---|---|
| Accounts | Sign in with Google and Apple | Needed to know who is "you" and who is "your person". Apple is required by the App Store if any other social sign-in is offered. |
| Database | Supabase (Postgres + Row Level Security) | Free tier is enough for a couple of users. One project, created by the app owner. |
| Live updates | Supabase Realtime | Pushes a change to the other phone within a second or two. |
| Offline | Local copy first | The app keeps working with no signal and syncs when it can. A signed-out user keeps today's behaviour exactly. |

## Data (small on purpose)
- `profiles` (id, display_name, avatar_url, parent_title)
- `shared_countdowns` (id, owner, title, type, target_at, time_zone, recurrence, dog, accent, notes, updated_at)
- `members` (countdown_id, user_id, role) so two people can see and edit the same row
- `dog_events` (id, countdown_id, user_id, kind: feed | water | treat | pet | ball | tickle, at) for the "who petted the dog" feed
- Hunger, thirst and joy are *derived* from the latest `dog_events`, so both phones agree without extra syncing.

Security rules: a row is readable and writable only by users listed in `members` for that countdown. No public access.
Photos stay on the device. Account deletion removes the profile and leaves shared countdowns to the other member.

## Invites
"Invite my person" makes a one-time link (valid 7 days). Opening it, after signing in, adds them to `members`.
It replaces today's copy-only link when both people are signed in.

## Build order
1. Create the Supabase project and turn on Google and Apple sign-in (owner).
2. Tables + Row Level Security + a test that a stranger cannot read or write a countdown.
3. Web: sign in, upload local countdowns, invite link, live feed.
4. Android and iPhone: same flow, with the sync code kept small and shared in design.
5. Push notification "Mama just fed Scott" (optional, later).

## Needs from the app owner
- A Supabase account and a new project (a paid plan is not needed to start).
- Google OAuth client IDs for web, Android and iOS, plus Apple "Sign in with Apple" setup (see SIGNIN_PLAN.md).
