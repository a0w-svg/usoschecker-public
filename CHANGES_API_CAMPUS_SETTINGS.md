# Student services, campus selection and Settings — v1.3.0

## Simpler screens

More uses shorter, translated navigation labels and readable supporting text. Cards,
documents, payment accounts, campus categories, GPA estimates and API documentation
keep secondary information behind explicit details. News previews are limited to
three lines; opening an article shows its text and verified source link.

Settings now has a short overview with separate pages for account access, student
profile, campus selection, synchronization, notifications, appearance/language,
widgets and help. The academic calendar is inside the student profile. Back returns to the overview. The selected page
survives Activity recreation; secrets and document receipt codes are not saved in
instance state. Feature switches replace routine raw OAuth-scope editing during setup;
advanced scope entry remains available. Existing credentials, permissions and preferences
are not silently changed by the layout update.

## Working news and campus relevance

A read-only request to the deployed `services/news/search` on 2026-10-19 returned
HTTP 200 with `total: 0`. A complete catalog is therefore not evidence that this
university publishes its website news in USOS.

The Warsaw public-news option uses the university's verified WordPress API at
`https://vistula.edu.pl/wp-json/wp/v2/posts`. It downloads at most 20 Polish and
20 English posts per refresh using an independent transport without USOS credentials.
Ukrainian users see an explicitly identified English fallback. USOS announcements
remain a separate source. Errors preserve the previous valid cache.

Campus detection reads active study programmes through `services/progs/student` and
examines the responsible faculty's verified identifier and ancestor path. Known branch
IDs were checked against the public `fac/search` catalog on 2026-10-19:

| ID | Campus |
| --- | --- |
| `0000` | Warsaw |
| `C0000` | Ciechanów |
| `G0000` | Gdańsk |
| `GB000` | Braniewo |
| `GT000` | Tczew |
| `O0000` | Olsztyn |
| `OK000` | Kętrzyn |
| `P0000` | Pułtusk |

Missing, unrecognized or conflicting affiliations require a manual selection. An
unknown faculty is not automatically assigned to Warsaw. The manual override is
stored with the account in the existing encrypted store and cleared on account replacement.

Branch news uses the documented `facpattern=+<branch ID>` selector. Warsaw uses
`=0000`, rather than `+0000`, which would include all descendant branches. Cached
news is tagged with its campus and hidden when that selection changes. Other branches
do not receive the untagged central website feed as a substitute for their own news;
where no branch announcements exist, the screen says so and offers a verified official
branch link when available. New branch IDs/links require a catalog update, not a guessed URL.

Campus information outside Warsaw uses the selected unit's account-derived name,
address and email, and verified branch links. Missing contact data does not fall back
to Warsaw's dean's office, address or opening hours. Personal grades, timetable,
payments and decisions remain scoped by the user's token; selecting a campus does
not hide any of that user's own records or grant access to another account.

## Payments and decisions

- Payments refresh independently of grades and the timetable, preserve their own
  timestamps/cache, and expose balances, interest, bonuses, current/available plans
  and installment schedules. Totals stay separate by currency and are described as
  installment totals, not an authoritative account balance.
- Remittance/settlement history loads independently via `payments/user_remittances`.
  Faculty bank accounts are labeled informational; a transfer must use the account
  attached to the relevant charge.
- Choosing a plan uses the normal student UI, rechecks its availability and displayed
  amounts/dates, asks for confirmation and sends one POST. Changed terms require a
  fresh review. It does not initiate a bank transfer.
- Document details remain readable offline. Downloads/UPO/UPD use granted actions.
  Receipt-code generation and receipt confirmation have dedicated forms, recheck
  `RECEIVE_DOCUMENT`, and send one POST. A documented `status: Err` response is shown
  as an error, not successful receipt. Code input is transient and protected from screenshots.
- Writes are not automatically replayed; uncertain network outcomes ask users to
  check USOS before trying again. Account identity is checked before action requests
  and before delivering the response.

## Reliability and translation

Streamed reads use a monotonic request budget and retain response size limits,
verified HTTPS and redirect rejection. Retryable failures without a valid Retry-After
have a conservative fallback cooldown. API Explorer handles absent scope lists and
rejects stale results after account changes. Authorization failures/cooldowns are
shared with the account state.

Polish, English and Ukrainian resources cover new controls and previously hardcoded
notification, reminder, attendance, GPA and error text. Cached service errors have
structured categories translated at display time. Resource checks enforce locale
coverage, matching format arguments and matching choice arrays. University-supplied
content retains the languages actually provided by the source.

## Verification scope

Regression tests use synthetic accounts and mock responses for receipt and payment
actions, account switching, cache failures, campus detection/filtering, localization
and navigation. The optional `LiveUniversityNewsTest` checks the real public news
transport without account credentials. Production account writes are not exercised
against a real student account. Release gates and artifact checks follow `RELEASE.md`
and `SECURITY.md`; previous releases and the existing signing identity are preserved.

Public sources used to verify branch links include the official Vistula pages for
[Gdańsk](https://gdansk.vistula.edu.pl/),
[Braniewo](https://gdansk.vistula.edu.pl/pl/braniewo),
[Tczew](https://gdansk.vistula.edu.pl/pl/node/286),
[Olsztyn/Kętrzyn](https://olsztyn.vistula.edu.pl/), and
[Pułtusk](https://pultusk.vistula.edu.pl/).
