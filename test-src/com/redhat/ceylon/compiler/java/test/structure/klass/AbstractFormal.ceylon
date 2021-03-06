/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
@nomodel
abstract class AbstractFormal() {
    shared void m() {
        test();
        test2();
        test3();
        test4();
    }
    shared formal void test();
    shared default void test2() { return; }
    shared default void test3() { return; }
    shared default void test4() { return; }
}
@nomodel
class ActualKlass() extends AbstractFormal() {
    shared actual void test() { return; }
    shared actual void test3() { return; }
    shared actual default void test4() { return; }
}
@nomodel
class ActualSubKlass() extends ActualKlass() {
    shared actual void test4() { return; }
}