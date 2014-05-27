#
# Copyright ${year} imperial
# Contact: imperial <weikun.wang11@imperial.ac.uk>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

use ExtUtils::MakeMaker;

use Config;
use lib qw(.. lib);
use SigarWrapper ();

my $installdir;
if ($ARGV[0]) {
    $installdir = $ARGV[0];
}
else {
    #check binary-dist location and svn source location
    for (qw(../../sigar-bin ../java/sigar-bin)) {
        if (-e $_) {
            $installdir = $_;
            last;
        }
    }
}

SigarWrapper::generate(Perl => '.');

my $archname = SigarWrapper::archname();
my $libname = 'sigar';
if ($archname) {
    $libname .= '-' . $archname;
}

my $ccname = $Config{ccname};

my $define = {
    gcc => '-Wall -Werror',
    cc_r => '-qhalt=w',
}->{$ccname} || '';

WriteMakefile(
    'NAME'		=> 'Sigar',
    'VERSION_FROM'	=> 'Sigar.pm',
    'LIBS'		=> ["-L$installdir/lib -l$libname"],
    'INC'		=> "-I$installdir/include",
    'DEFINE'            => $define,
    'depend'            => { 'Sigar.c' => 'Sigar_generated.xs' },
);
