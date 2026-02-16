# Warning Policy

PWSEditor source code must remain clean under `-Xlint:all`.

## Standard
- No compiler warnings are allowed in project sources.
- New changes must not introduce any lint warnings.

## Exception
- `@SuppressWarnings("this-escape")` is allowed only when justified by Swing/UI lifecycle patterns (for example, listener registration or framework callbacks during construction).
- Any `this-escape` suppression must stay narrow (class or constructor level only) and should not hide other warning categories.

## Verification
Use a full-source lint compile before finalizing changes:

```bash
javac -Xmaxwarns 10000 -Xlint:all -d /tmp/pws-lint-all \
  -cp "lib/pdfbox-2.0.29.jar:lib/fontbox-2.0.29.jar:lib/commons-logging-1.2.jar:lib/pdfbox-graphics2d-0.6.0.jar:lib/graphics2d-3.0.3.jar" \
  -sourcepath src $(find src -name "*.java")
```
