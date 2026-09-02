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
2. **Corners.** The user drags four handles onto the centres of the four
   corner hexes — `r1c1`, `r1c5`, `r9c1`, `r9c5`. The printed map area itself
   has no crisp corner to aim at, but a corner hex's centre is visually
   unambiguous, and those four centres form an exact rectangle in board
   space, which is what makes them a sound basis for the transform. Manual by
   design: it is a few seconds of work, it never fails, and it removes an
   entire class of detection bug from version one. Automatic corner detection
   is a later optimisation, not a prerequisite.
3. **Warp.** `android.graphics.Matrix.setPolyToPoly` plus `Canvas.drawBitmap`
   produce a canonical top-down image at a fixed resolution. The platform
   provides the same four-point perspective mapping OpenCV would, so pulling
   in OpenCV's Android distribution — roughly 100MB of native libraries — for
   one function buys nothing.
4. **Crop.** Hex centres come from the shared board geometry. Each crop is a
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

Steps 5 and 7's classification and confidence highlighting are not built yet.
Today, tile type is entered by hand on the review grid: every hex defaults to
`empty`, and the user taps a hex to set its type from a dialog. Owner colour
is read automatically, as step 6 describes. Plan 3 — a model that classifies
tile type from each crop — changes exactly one thing: the `empty` default in
the review grid becomes a classification with a confidence, and hexes below a
confidence threshold get highlighted. Capture, anchors, warp, the editable
grid, and the score screen stay as they are.

## Modules

```
:app        Kotlin, CameraX, Jetpack Compose. Capture, corner drag,
            board warp, review grid, score screen.
:vision     Pure Kotlin. Canonical image sizing, per-hex crop boxes,
            anchor geometry, HSV colour reading from a pixel buffer.
            No Android dependencies; anything needing android.graphics,
            including the warp itself, lives in :app.
            Input: hex geometry from :scoring, board id, pixel buffers.
            Output: crop boxes, warp anchor points, player colour reads.
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
the hex, each axis normalised to 0.0..1.0 against the bounding box of hex
centres, not the board outline: 0.0 and 1.0 are the centres of the border
hexes on that axis, half a hex short of the warped canonical board image's
edges. Because each axis is normalised independently, the width-to-height
ratio of the centre bounding box is published alongside the geometry so a
consumer — `:vision`, in particular — can restore true proportions before
doing distance maths. The half-width and half-height of a hex, in the same
normalised units, are published too, so `:vision` can size a crop that
reaches the true board edge at every border hex instead of stopping at the
centre bounding box.

The canonical image — the flat, head-on view a photo is warped into — spans
half a hex beyond the centre bounding box in every direction, so every hex's
crop box falls inside it with nothing clipped at the border. Its pixel size
is derived from one parameter, how tall a single hex crop should be, so a
caller picks image resolution by picking crop quality.

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

- **Bad corner placement.** The warped preview is shown before the grid is
  filled in. If it looks wrong, the user redrags. No silent failure.
- **Low confidence.** Not built yet — there is no confidence value until
  Plan 3's model exists. Once it does, flag hexes below a threshold on the
  review grid rather than hiding the uncertainty. Today every hex simply
  defaults to `empty` and is set by hand.
- **Impossible states.** A greenery on an ocean-reserved hex is flagged for
  confirmation rather than rejected — house rules and worn components exist.
  Cube-colour validation against a declared player roster is not built yet.
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
- `:vision` — unit tests over the canonical image mapping, the crop plan, the
  anchor geometry (including the rectangle claim above), and the cube colour
  reader against synthetic pixel buffers covering each player colour, warm
  light, and near-misses like shadowed board and Martian soil.
- Model — a held-out set of real photo crops, reported as a confusion matrix.
  Greenery-versus-special confusion is expected and is the number to watch.

## Open questions

- Should Capital and Commercial District score their card VP once the app can
  distinguish special tiles? Revisit after seeing how often special tiles are
  misread in practice.
- Is automatic corner detection worth adding after version one, or does the
  drag turn out to be fast enough to keep permanently?

## Board data

The three boards' ocean-reserved hex sets were transcribed from the physical
printed boards and are held in `Board.kt`. Each board has twelve, which a test
asserts, alongside a test that every id is a real hex. No scoring rule depends
on them, since oceans score nothing either way; they drive the review screen's
ocean hint and the warning when a greenery is placed on a space printed as
ocean.
