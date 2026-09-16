# Sources and verification dates

Everything the app states about a card or program is a snapshot with a
date. This file lists where each snapshot came from and when it was last
checked. "Checked" means a person or the research pass read the issuer's or
program's own public page on that date; it does not mean the terms cannot
have changed since. Verify anything that matters before acting.

## Credit cards with full rule provenance (checked 2026-09-14)

These eleven entries carry `RuleProvenance` in `CardCatalog.kt` (caps, cap
periods, credits, period basis, perks). The freshness shown in the app is
computed from this date: verified ≤180 days, aging ≤400 days, stale after.

| Card | Source |
|---|---|
| Chase Sapphire Reserve | https://creditcards.chase.com/rewards-credit-cards/sapphire/reserve |
| Chase Freedom Flex | https://creditcards.chase.com/cash-back-credit-cards/freedom/flex |
| Chase Ink Business Cash | https://creditcards.chase.com/business-credit-cards/ink/cash |
| American Express Gold | https://www.americanexpress.com/us/credit-cards/card/gold-card/ |
| American Express Platinum | https://www.americanexpress.com/us/credit-cards/card/platinum/ |
| American Express Blue Cash Preferred | https://www.americanexpress.com/us/credit-cards/card/blue-cash-preferred/ |
| American Express Blue Cash Everyday | https://www.americanexpress.com/us/credit-cards/card/blue-cash-everyday/ |
| Capital One Venture X | https://www.capitalone.com/credit-cards/venture-x/ |
| Citi Custom Cash | https://www.citi.com/credit-cards/citi-custom-cash-credit-card |
| Discover it Cash Back | https://www.discover.com/credit-cards/cash-back/it-card.html |
| U.S. Bank Cash+ | https://www.usbank.com/credit-cards/cash-plus-visa-signature-credit-card.html |

Known caveats recorded in the entries themselves: Amex credits require
enrollment and most reset on the calendar year; Uber Cash is monthly with a
December bonus; the Sapphire Reserve and Venture X travel credits reset on
the cardmember anniversary; several 2025 product refreshes changed amounts,
so the app labels these as "verify current amounts".

## Remaining catalog cards (~75 entries)

Curated from public issuer pages during the September 2026 research pass,
with a handful of older entries from early 2025. They have no per-rule
provenance yet, so the app shows the catalog-wide "as of" note and treats
their caps as unknown rather than zero. Adding `provenance = RuleProvenance(...)`
to an entry promotes it to the table above.

## Loyalty programs (`LoyaltyProgramCatalog.kt`)

Account and redemption links are the programs' own pages (Marriott, Hilton,
Hyatt, IHG, Wyndham, Choice, Best Western, Radisson, Accor, Delta, United,
American, Southwest, JetBlue, plus the shop and dining programs listed in the
README). Tier ladders, expiry rules and the award-stays-count-for-status flag
were read from each program's terms during the same pass. Where a rule could
not be pinned down the field is `null` and the app says "unknown" (for
example the award-stay rule for Delta, United, JetBlue and Southwest, and
tier thresholds for Atmos Rewards and Radisson Rewards).

## Transfer partners and point values (`TransferPartnerCatalog.kt`, `RewardCurrency`)

Transfer ratios come from the bank programs' partner pages. Cents-per-point
estimates are the kind of figures independent trackers publish and are
labelled as estimates everywhere they appear; the user's own valuation, set
in Settings, replaces them.

## Award inventory

- seats.aero Partner API: https://seats.aero/partnerapi (documentation read
  2026-09-14; requires a Pro subscription and a Partner-Authorization key).
  Results are cached availability with an "as of" timestamp and are labelled
  as such in the app.
- AI research leads: web search through the Anthropic API; every lead
  carries the date of the source it cites and is labelled "research", never
  "available".

## Loyalty programmes outside the United States (checked 2026-09-16)

Australia, the UK, Canada and South Africa were added to the programme
catalog on this date. Only the expiry rules below could be checked against a
programme's own terms or a primary write-up of them; everything else about
these nineteen entries is deliberately absent rather than estimated. In
particular, none of them carries a tier ladder or a cents-per-point
valuation: the ladders for several were revised recently, and a valuation in
the app is denominated in US cents, which would need an exchange rate the app
does not hold. `valuationIsKnown = false` is set on all of them, so the app
reports "not in the app for this programme" instead of a zero.

| Programme | Expiry recorded | Source |
|---|---|---|
| Qantas Frequent Flyer | 18 months of inactivity | https://www.qantas.com/en-au/frequent-flyer/member-support/points-expiry |
| Velocity (Virgin Australia) | 24 months of inactivity | https://www.pointhacks.com.au/velocity/points-expiry/ |
| Flybuys | 12 months of inactivity | https://help.flybuys.com.au/hc/en-gb/articles/9103578419983-Do-my-points-ever-expire |
| Everyday Rewards | not established | window not confirmed; no countdown shown |
| British Airways Executive Club (Avios) | 36 months of inactivity | https://www.britishairways.com/travel/execclub/ |
| Virgin Atlantic Flying Club | no inactivity expiry since 2020 | https://flywith.virginatlantic.com/us/en/flying-club/members/flying-club-news/exciting-changes-to-your-miles.html |
| Tesco Clubcard | none on the balance; vouchers expire 24 months after issue | https://www.moneysavingexpert.com/news/2026/08/tesco-clubcard-vouchers-expiring/ |
| Nectar | 12 months of inactivity can close the account | https://www.clearscore.com/learn/credit-cards/tesco-clubcard-sainsburys-nectar-points-warning |
| Boots Advantage Card | 12 months of inactivity; account closed after four years | https://www.boots.com/shopping/advantage-card/advantage-card-help |
| Air Canada Aeroplan | 18 months, suspended until 29 November 2026 | https://www.aircanada.com/ca/en/aco/home/aeroplan/news/points-expiry-suspended.html |
| WestJet Rewards | not established (mid-transition to points) | https://www.westjet.com/en-ca/rewards/terms-conditions |
| PC Optimum | 12 months of inactivity | https://www.realcanadiansuperstore.ca/en/help/pc-optimum/understanding-pc-optimum-point-expiry-and-redemption-amounts |
| Scene+ | 24 months of inactivity; credit-card holders exempt | https://www.rewardscanada.ca/expiry.html |
| AIR MILES (Canada) | account expires after 24 months of inactivity | https://www.airmiles.ca/ |
| SAA Voyager | calendar-anchored, not a rolling clock; not modelled | https://voyager.flysaa.com/terms-conditions |
| eBucks (FNB) | no expiry | https://www.ebucks.com/web/eBucks/aboutus/FAQ.jsp |
| Clicks ClubCard | not established (voucher-based) | https://clicks.co.za/clubcard |
| Pick n Pay Smart Shopper | not established | https://www.pnp.co.za/smartshopper |
| Discovery Vitality / Discovery Miles | not established | https://www.discovery.co.za/portal/individual/vitality |

Where the expiry column says "not established", `ExpirationPolicy.inactivityMonths`
is null and the app draws no countdown; the summary string on screen says why.
Several of these rows cite a secondary source rather than the programme's own
terms page, which is why the app labels every one of them as a snapshot to
verify.

## Bank transactions

- Plaid: https://plaid.com/docs/ (Transactions product; Link SDK 5.5.1).
  Pricing and the free developer allowance are on https://plaid.com/pricing/
  and change; the README quotes them as of 2026-09-14.

## Anthropic API

- https://docs.anthropic.com (Messages API, web search tool). Model and
  pricing are set in `data/remote/anthropic/`; usage is metered per email
  read and per question asked.
