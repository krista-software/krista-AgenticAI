# Release Notes

## Version 1.0.2

* **Krista Service APIs Java**: 1.0.117
* **Global Catalog Version**: 1.0.61
* **Appliance Release Version**: 3.5.2

## New Changes
* Support for default fields added

## Limitations
* No support for labels, articles and complex fields.
* Secured field values can not be masked in chat mode.
* When timezone is not specified for date-time related fields, we can not identify browser's timezone in chat mode and LLM can extract incorrect date-time values.
* Workspace and Agent level instructions are not supported.

## Known Issues
* [Secured attribute is not masking value in chat mode](https://antbrains.atlassian.net/browse/KR-19003)
* [No loading conversation dots present in chat mode](https://antbrains.atlassian.net/browse/KR-19034)
* [Image and link in PMR not visible in chat mode](https://antbrains.atlassian.net/browse/KR-19041)
* [In chat mode conversation coming in english only although language hindi selected](https://antbrains.atlassian.net/browse/KR-19044)
* [Time mismatch in chat mode from form mode](https://antbrains.atlassian.net/browse/KR-19133)
* [Pre-filled values from Form Mode not retained in Chat Mode](https://antbrains.atlassian.net/browse/KR-19152)
* [Sidebar getting skip in chat mode](https://antbrains.atlassian.net/browse/KR-19056)
