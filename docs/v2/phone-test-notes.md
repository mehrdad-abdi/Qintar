# Phone test of 2.0.0: findings

Collected while testing release 2.0.0 on the owner's phone (Samsung SM-A346B), 2026-11-22.
Each item becomes part of one ticket and is planned together with the others.

Kind: bug / change / idea. Status: open until planned.

**Rule for bugs:** first write a test that reproduces the bug and fails, then fix it, so the test keeps it from coming back.

| # | Kind | Screen | What was seen or asked | Notes / evidence |
|---|---|---|---|---|
| 1 | bug? | Reader, audio | Player bar showed "Al-Faatiha 1" while ayah 1:2 was highlighted (seen on the emulator before release) | Not investigated; the basmala may be labelled as ayah 1 |
| 2 | change | Onboarding, About | Show the hadith the ladder comes from. Onboarding's first page: Bismillah, then the hadith in Arabic, English and Persian, so the user sees where the ranks come from. About (Settings): a place to read it again. | DESIGN §4.4 decision Q2 is wrong (the owner meant something else): rewrite that section and Q2. v1 had the text (`about_hadith_*` in v1-final strings), but its Arabic used Persian ی ک and emoji: needs a clean Arabic text (ي ك, no emoji), a citation (source book), and checked English/Persian translations. |
| 3 | bug | Onboarding, goal and reminder page | Tried to turn the reminder on during onboarding on the phone; it did not get set. | On the phone the notification permission is granted (user 0). Exact symptom to confirm (switch would not turn on, no permission dialog, or on but no reminder). Code: `OnboardingScreen.kt` `GoalAndReminderPage` (notification gate → `setReminderEnabled`). Reproduce in a test first. |
| 4 | change | Read, surah list | Every surah has a full green bar under it, so it looks as if every surah is "done"; feels odd. | The bar is all-time coverage, "share of ayahs ever read" (DESIGN §5.3; `ReadScreen.kt` ~316, `observeSurahCoverage`). After v1's 133k ayahs every surah is at 100%, so it says nothing. The bar plus the row divider also makes a double line. Decide what it should mean (current khatm? recent period? remove?). Screenshot: phone, Read tab, 2026-11-22 11:55. |
| 5 | change | Reader, ayah row | Tapping the star on an ayah should open the add-bookmark editor with that ayah filled in; the user adds a description and may set the end of the range. | Today the star toggles a one-tap "starred" bookmark (`viewModel.onStar`); the ⋮ menu's "Add bookmark" already opens `BookmarkEditorRoute.forAyah`. Decide: what the filled star means afterwards (the ayah is in any bookmark?), what tapping a filled star does (open that bookmark?), and what happens to existing starred bookmarks. Update DESIGN (star / R-BMK). |
| 6 | change | Bookmark editor | Remove the Juz option: bookmarking a juz makes no sense. | UI only. |
| 7 | change | Bookmark editor | A range stays inside one surah: pick a surah, then a start ayah and an end ayah. | UI only; the model keeps general `AyahRange`. Existing cross-surah bookmarks (v1 carry-over, page ranges) must still open and edit sensibly. |
| 8 | change | Bookmark editor, preview | The preview shows the start of the bookmark and "…" when long (keep), then the end of the range after the dots, so the user sees from where to where. | |
| 9 | change | Bookmark editor | Remove "readings per day" (repeat target) from the UI; deprecate it in the model, do not delete it (may come back soon). | Existing bookmarks with a target: decide how they behave while hidden (treat as 1?). |
| 10 | change | Bookmark editor | Hide the schedule / time options: too complicated. Keep the code (it was a feature request), just not shown. | Existing scheduled bookmarks: decide whether their schedule still applies while hidden. |

Owner on items 7, 9, 10: no such existing bookmarks on the phone and the owner is the only user, so no compatibility handling is needed.

| # | Kind | Screen | What was seen or asked | Notes / evidence |
|---|---|---|---|---|
| 11 | change | Groups (model meaning) | A group is no longer time-based: it is just a set of bookmarks. Choose the group when adding a bookmark; add several bookmarks when creating or editing a group. A default "Ungrouped" holds bookmarks without a group. | Model already has `Bookmark.groupId: Long?` (at most one group). Schedules hidden (item 10). DESIGN §4.7 and §5.6 to rewrite. |
| 12 | change | Bookmarks screen | Redesign: no "Nothing scheduled". Filter [All] [Unread]. Groups with their bookmarks in one list; two levels of collapse (group hides its bookmarks, bookmark hides its ayahs). Main use: read every bookmark by scrolling one list, without entering and leaving each bookmark. | Design to be confirmed with the owner, then build a first version to try and refine. |
| 13 | bug/change | Bookmarks, play | Play in the list continues through the following bookmarks in list order; it must not stop at the end of the first bookmark. | |

Decisions for 11–13 (owner, 2026-11-22): one list, groups then built-in Ungrouped (bookmarks with no group; cannot be renamed or deleted); on open, groups expanded and bookmarks' ayahs collapsed; in Unread, a bookmark finished while on the screen stays (collapsed, done) until the screen is left; "read today" counts only readings made from the bookmark (unchanged); play continues through following bookmarks in list order, across groups, skipping read ones under Unread; the list follows the recited ayah.
| 14 | change | Reader (bookmark, group) and Bookmarks list | No "Mark page" inside a bookmark. At the end of a bookmark: one "Mark read" that marks all its ayahs. Even when the bookmark ends on a page's or surah's last ayah, only that one option shows. | Bookmarks list: a footer under each expanded bookmark (built in the first version). Reader bookmark/group scopes: drop the page divider's action. |
| 15 | change | Reader (khatm, browse) | At the end of a surah, add "Mark surah read", next to the existing "Mark page" at the end of a page. | When a surah ends on the page's last ayah, both show. |
| 16 | change | Bookmarks list, group headers | A long description of a bookmark or a group can be expanded (shows two lines, tap for all). | Built in the first version of the list. |
| 17 | change | Audio settings, player | New playback setting: stop before a sajda ayah and wait until the user taps play on that ayah. | `PlaybackQueue.after` / `PlaybackService`; `QuranIndex.sajdaOf`. Applies to obligatory and recommended sajdas? (to decide) |
| 18 | change | Player | After a sajda ayah has played, play a sajda dhikr. | Needs: which dhikr text, a recording (bundled, licence) and whether it is on by default; offline. |
| 19 | change | Insights → Revisit | Revisit makes no sense as it is: grouped per mushaf page it always shows the same pages. At least make it per bookmark. | DESIGN §4.8 (R-INS-3). Proposal: list bookmarks by the last day each was read (from the bookmark), oldest / never first, e.g. "Al-Mulk · last read 23 days ago"; a tap opens it. Check also why the page list never changes (v1 carry-over: imported days may give every page the same "last read"). |

## Status (branch `bookmarks-list`, 2026-11-22)

| # | Status |
|---|---|
| 1 | Not reproduced: the player bar and the highlight come from the same player state. Watch for it while testing. |
| 2 | Done. Hadith source (book) still to be supplied by the owner. |
| 3 | Not reproduced in a test (switch on → saved, time shown). Needs the exact symptom; the test stays as a guard. |
| 4 | Done: bar = coverage in the current khatm, in place of the divider. |
| 5 | Done: star opens the editor; filled star = ayah in a bookmark, opens that bookmark. |
| 6, 7, 8 | Done. |
| 9, 10 | Done (hidden; kept in model/code). |
| 11, 12, 13, 16 | Done (first version of the list, to refine after testing). |
| 14, 15 | Done (list and reader). |
| 17 | Done (setting, off by default). |
| 18 | Open: needs the dhikr text and a recording. |
| 19 | Done: Revisit per bookmark. |
