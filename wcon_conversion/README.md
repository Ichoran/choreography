# WCON Conversion

This directory contains conversion utilities that convert old-style MWT
output to and from the WCON format.

## To compile

You need to download the Tracker Commons project at [https://github.com/openworm/tracker-commons.git](https://github.com/openworm/tracker-commons.git)
and run `sbt package` in the `src/scala` directory.

Then take the resulting JAR (probably in
`target/scala-2.12/tracker-commons_2.12-0.2.0.jar`) and place it in the
`lib` directory here.  Eventually this will be handled by a dependency,
but the Tracker Commons Scala version will need to be released on Maven
first.

Then `sbt assembly` here.

## To run

You'll need lots of extra memory; the entire thing is converted in memory.  Don't be shy!

Run with something like one of these:

    sbt -J-Xmx4G 'run myFile'
    java -Xmx4G -jar target/scala-2.12/MwtWconConvert_2.12_assembly-0.2.0 myFile
    java -Xmx4G -cp target/scala-2.12/MwtWconConvert_2.12.assembly-0.2.0 mwt.MwtWconConvert myFile

(To be able to do the latter two, you'll need to run `sbt assembly`; then you have a single Jar that you can deploy wherever you need it.)

If `myFile` is a `.wcon` file, it will produce a zip file in old MWT format
containing all the usual files save for the PNG.

If `myFile` is a directory, or a `zip` file containing a directory, it will
produce from that a WCON file converted from old MWT format.

If anything goes wrong, it will die horribly, possibly but not necessarily
with an informative error message.

If you want to convert more than one thing, just list them on the command-line: `myFile1 myFile2 ... myFileN`
