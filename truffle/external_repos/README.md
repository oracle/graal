# External maven repositories

As part of Truffle documentation we provide two maven projects that serve as a
starting point for developing a Truffle language
([Simplelanguage](https://github.com/graalvm/simplelanguage)) or a Truffle tool
([Simpletool](https://github.com/graalvm/simpletool)).

To simplify keeping these projects in sync with the development of Truffle, we
develop both Simplelanguage and Simpletool as part of Truffle. 

On the other hand, to ensure that the maven configuration of these projects are
continuously tested, we keep all the necessary project files in this directory,
as well as the `populate.py` script which extracts the Simplelanguage and
Simpletool source from Truffle and populates the `simplelanguage/` and
`simpletool/` directories.

On a GraalVM release, the populated content of these directories are used to
update the individual repositories.
