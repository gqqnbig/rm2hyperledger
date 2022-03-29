Use JDK 11


It's suggested to add the following VM option for adjusting logger output.

```
-Djava.util.logging.SimpleFormatter.format="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s %5$s%6$s (at %2$s)%n"
```
