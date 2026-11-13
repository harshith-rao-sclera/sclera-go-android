#!/usr/bin/perl
# Usage: fiximports.pl <compiler-log>  (run from repo root)
# Adds `import pkg.Name` for each "Unresolved reference 'Name'" error, using an index of top-level declarations.
use strict; use warnings; use File::Find;
my %idx;   # name -> {pkg=>1}
my %pkgOf; # file -> package
find(sub {
  return unless /\.kt$/; my $f = $File::Find::name; open my $h, '<', $_ or return; my $pkg = '';
  while (<$h>) {
    $pkg = $1 if /^package\s+([\w.]+)/;
    if (/^(?:(?:public|internal|inline|data|enum|sealed|abstract|open|value|annotation|const|operator|infix|tailrec|suspend)\s+)*(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?(\w+)/) {
      $idx{$1}{$pkg} = 1;
    }
  }
  $pkgOf{$f} = $pkg;
}, 'app/src/main/java', 'app/src/test/java');
my %need; # file -> {import=>1}
open my $log, '<', $ARGV[0] or die;
while (<$log>) {
  next unless m{^e: file:///(.+?\.kt):\d+:\d+ Unresolved reference '(\w+)'};
  my ($file, $name) = ($1, $2); $file =~ s{^.*?(app/src/)}{$1};
  my $own = $pkgOf{$file} // '';
  if ($name eq 'R') { $need{$file}{'com.sclera.sclerago.R'} = 1; next }
  my @p = grep { $_ ne $own } sort keys %{ $idx{$name} // {} };
  if (@p == 1) { $need{$file}{"$p[0].$name"} = 1 }
  elsif (@p > 1) { my @pp = grep { /presentation/ } @p; if (@pp == 1) { $need{$file}{"$pp[0].$name"} = 1 } else { print "AMBIGUOUS $file $name: @p\n" } }
  else { print "UNKNOWN $file $name\n" }
}
for my $file (sort keys %need) {
  open my $h, '<', $file or die "$file"; my @l = <$h>; close $h;
  my %have = map { /^import\s+([\w.]+)/ ? ($1 => 1) : () } @l;
  my @add = grep { !$have{$_} } sort keys %{ $need{$file} }; next unless @add;
  my ($at) = grep { $l[$_] =~ /^import / } 0..$#l; ($at) = grep { $l[$_] =~ /^package / } 0..$#l unless defined $at;
  my $nl = ($l[0] =~ /\r\n$/) ? "\r\n" : "\n";
  if ($l[$at] =~ /^package /) { splice @l, $at + 1, 0, $nl, map { "import $_$nl" } @add }
  else { splice @l, $at, 0, map { "import $_$nl" } @add }
  open $h, '>', $file or die; print $h @l; close $h;
  print "fixed $file: @add\n";
}
