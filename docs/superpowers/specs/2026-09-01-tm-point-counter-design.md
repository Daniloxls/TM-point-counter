# TM Point Counter — Design

Date: 2026-09-01
Status: Approved for planning

## Purpose

An Android app that photographs a Terraforming Mars board and reports the
victory points each player has earned from tiles placed on the map. The user
frames the board, corrects anything the app read wrong, and gets a per-player
score breakdown.

## Scope

In scope: victory points derived from the map itself.

- Greenery tile: 1 VP to its owner.
- City tile: 1 VP to its owner per adjacent greenery tile, regardless of who
  owns the greenery.
- Ocean tiles and special tiles: 0 VP on their own.
- Boards: Tharsis, Hellas, Elysium.
- Ownership: standard player cubes in the five official colors (blue, red,
  green, yellow, black).

Out of scope: terraform rating, milestones, awards, points printed on project
cards, and resources held on cards. These are not readable from a photo of the
map, and the app does not attempt to guess them. The score screen labels its
output "map points" so it is never mistaken for a final game score.

Deliberately deferred: Capital's "1 VP per adjacent ocean" and Commercial
District's "1 VP per adjacent city" are board geometry, but they are card
effects rather than map rules, and they require the app to tell one special
tile from another. Capital is treated as an ordinary city tile for now. See
Open Questions.

## Approach

The map layout is fixed. Each supported board has 61 hexes at coordinates that
never change, so the app does not need to find tiles anywhere in the image. It
needs to answer one small question 61 times: what is on this hex?

That turns a hard object-detection problem into a cheap classification problem,
and it means the model can be trained largely on synthetic data.

## Pipeline

1. **Capture.** CameraX still capture. An on-screen guide shows the board
   outline so the user frames roughly square-on.
2. **Corners.** The user drags four handles onto the corners of the printed
   map area. Manual by design: it is a few seconds of work, it never fails, and
   it removes an entire class of detection bug from version one. Automatic
   corner detection is a later optimisation, not a prerequisite.
3. **Warp.** OpenCV `getPerspectiveTransform` plus `warpPerspective` produce a
   canonical top-down image at a fixed resolution.
4. **Crop.** Hex centres come from the board definition file. Each crop is a
   square region around the centre, resized to 64x64.
5. **Classify.** A TFLite model labels each crop as one of
   `empty | ocean | greenery | city | special` and returns a confidence.
6. **Owner colour.** For any non-empty, non-ocean hex, the player cube colour
   is read from an HSV histogram of the crop centre — no model needed. Black
   is identified as low saturation plus low value, which separates it from the
   reddish map surface.
7. **Review.** The warped image is shown with the classification overlaid.
   Every hex is tappable and editable. Hexes below a confidence threshold are
   highlighted so the user's attention goes where it is needed. Special tiles
   are shown as a single `special` class; if the user needs one identified
   precisely, they pick it from a list.
8. **Score.** The confirmed grid is handed to the scoring module, which is
   pure Kotlin and knows nothing about cameras or models.

## Modules

```
:app        Kotlin, CameraX, Jetpack Compose. Capture, corner drag,
            review grid, score screen.
:vision     Warp, crop, TFLite inference, HSV colour reading.
            Input: bitmap + four corners + board id.
            Output: List<HexRead(hexId, type, owner, confidence)>.
:scoring    Pure Kotlin, no Android dependencies, no third-party
            dependencies. Computes the shared hex geometry, holds
            per-board ocean-reserved hex ids, and applies the VP rules.
            Input: confirmed grid. Output: per-player breakdown.
tools/      Python. Synthetic data generation, training, TFLite export.
```

`:scoring` is separated because it is the part that must be exactly right, and
it is the part that is trivially testable once it has no Android dependencies.
`:vision` can be wrong and the user will catch it on the review screen;
`:scoring` cannot be wrong at all.

## Board definition format

All three boards share one geometry, so it is computed rather than
hand-maintained as data: a radius-4 hexagon in axial coordinates, giving rows
of 5-6-7-8-9-8-7-6-5 for 61 hexes. Hex ids have the form `r<row>c<col>`, rows
1-9 top to bottom and columns 1-N left to right within a row. For each hex the
geometry gives its axial coordinates, its neighbour list, and the centre of
the hex in the warped canonical board image, normalised per-axis to 0.0..1.0.
Because each axis is normalised independently, the width-to-height ratio of
the centre bounding box is published alongside the geometry so a consumer —
`:vision`, in particular — can restore true proportions before doing distance
maths.

Per-board data reduces to the set of ocean-reserved hex ids, which the review
screen uses as a hint and the app uses as a sanity check.

## Model and training data

Architecture: MobileNetV3-Small at 64x64 input, five output classes, exported
to TFLite with int8 quantisation. Expected size well under 2MB. Sixty-one
inferences at this size run in well under a second on a mid-range phone.

Training data comes from three sources, in this order:

1. **Synthetic.** Composite scanned tile images onto scanned board images at
   the known hex coordinates, then apply random perspective, lighting
   gradients, colour temperature shifts, shadows, blur, and JPEG noise. This
   generates tens of thousands of correctly labelled crops for the cost of
   writing one generator script.
2. **Real photos.** A few hundred crops from real board photos taken in normal
   playing conditions, used to fine-tune. This is what closes the gap between
   synthetic cleanliness and a real table.
3. **User corrections.** Every edit on the review screen is a labelled example.
   Stored locally with the crop, exportable by the user. Nothing is uploaded.

The synthetic-first order matters: it means training can start before any real
photos exist, and real photos are only needed for the final fine-tune.

## Error handling

- **Bad corner placement.** The warped preview is shown before classification.
  If it looks wrong, the user redrags. No silent failure.
- **Low confidence.** Flagged on the review grid rather than hidden. The score
  screen reports how many hexes were user-corrected.
- **Impossible states.** A greenery on an ocean-reserved hex, or a cube colour
  that matches no player in the game setup, is flagged for confirmation rather
  than rejected — house rules and worn components exist.
- **Wrong board selected.** The user selects the board before capture. No
  automatic board identification in version one.
- **Camera or model failure.** The review grid is fully usable with everything
  set to `empty`, so the app degrades to manual entry rather than to nothing.

## Testing

- `:scoring` — unit tests over hand-built grids: a lone greenery, a city with
  no adjacent greenery, a city adjacent to three greeneries owned by three
  different players, adjacency at the board edge, an empty board. These are
  the tests that must never be allowed to fail. Also a geometry test asserting
  61 hexes, symmetric neighbour relations, no hex claiming itself as a
  neighbour, and centre coordinates inside the unit square, plus a data test
  per board asserting its ocean-reserved ids are real hexes.
- `:vision` — fixture test over a handful of stored photos with known correct
  grids, asserting an accuracy floor rather than an exact match.
- Model — a held-out set of real photo crops, reported as a confusion matrix.
  Greenery-versus-special confusion is expected and is the number to watch.

## Open questions

- Should Capital and Commercial District score their card VP once the app can
  distinguish special tiles? Revisit after seeing how often special tiles are
  misread in practice.
- Is automatic corner detection worth adding after version one, or does the
  drag turn out to be fast enough to keep permanently?

## Outstanding data

The three boards' ocean-reserved hex sets are not yet filled in. Transcribing
them means reading the physical printed boards by hand, which is deferred
work rather than a design gap. No scoring rule depends on them, since oceans
score nothing either way. Until they are filled in, the ocean hint on the
review screen has nothing to show.
