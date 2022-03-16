//ChuList v1.9, ©2020 Pecacheu. Licensed under GNU GPL 3.0

//An actually properly implemented List class? With error checking!? Imagine that!

package net.forestfire.elevators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@SuppressWarnings("serial")
public class ChuList<E> extends ArrayList<E> {
public int length=0; //<- JavaScript Compatibility (Maybe not the best idea, though)
public ChuList() {
	super();
}
public ChuList(int initialCapacity) {
	super(initialCapacity);
}
public ChuList(Collection<? extends E> c) {
	super(c); length=super.size();
}

//Construct with initial elements:
@SafeVarargs
public ChuList(E... elements) {
	super();
	for(int i=0,l=elements.length; i<l; i++) super.add(elements[i]);
	length = super.size();
}

@Override
public boolean add(E item) {
	super.add(item); length = super.size(); return true;
}

//JavaScript Compatibility:
public void push(E item) {
	add(item);
}

//Now, this is just common-sense functionality:
public String join(String sep) {
	String str = ""; for(int i=0,l=super.size(); i<l; i++)
		str += (i==0?"":sep)+super.get(i); return str;
}

@Override
public void add(int index, E item) {
	if(index < 0) return;
	if(index >= super.size()) super.add(item);
	super.add(index, item); length = super.size();
}

@Override
public E set(int index, E item) {
	if(index < 0) return null;
	while(index >= super.size()) super.add(null);
	E set = super.set(index, item);
	length = super.size(); return set;
}

@Override
public E get(int index) {
	if(index < 0) return null;
	if(index >= super.size()) return null;
	return super.get(index);
}

@Override
public E remove(int index) {
	if(index < 0) return null;
	if(index >= super.size()) return null;
	E rem = super.remove(index);
	length = super.size(); return rem;
}

@Override
public boolean remove(Object o) {
	boolean rem = super.remove(o);
	length = super.size(); return rem;
}

@Override
public void clear() {
	super.clear(); length = 0;
}

//Actually Working toString For Debugging:
@Override
public String toString() {
	String str = "["; for(int i=0,l=super.size(); i<l; i++)
		str += (i==0?"":", ")+super.get(i); str += "]"; return str;
}

public ChuList<E> addAll(int index, E[] arr) {
	for(int i=0,l=arr.length; i<l; i++) add(index+i, arr[i]);
	return this;
}

public ChuList<E> addAll(E[] arr) {
	for(int i=0,l=arr.length; i<l; i++) super.add(arr[i]);
	return this;
}

public ChuList<E> addAll(ArrayList<E> arr) {
	for(int i=0,l=arr.size(); i<l; i++) super.add(arr.get(i));
	return this;
}

@Override
public ChuList<E> subList(int fromIndex, int toIndex) {
	if(fromIndex < 0) fromIndex = 0; if(toIndex > super.size()) toIndex = super.size();
	List<E> list; if(fromIndex > toIndex) list = super.subList(toIndex, fromIndex);
	else list = super.subList(fromIndex, toIndex); return new ChuList<E>(list);
}

	/*@SuppressWarnings("unchecked")
	@Override
	public E[] toArray() {
		int size = super.size(); Object[] arr = new Object[size];
		for(int i=0; i<size; i++) arr[i] = super.get(i); return ((E[])arr);
	}*/

//Unchanged Methods:

//size()
//ensureCapacity()
//trimToSize()
//indexOf()
//lastIndexOf()
//forEach()
//isEmpty()
//equals()
//toArray()
//hashCode()

//--------- Unimplemented Methods:

//TODO Add Error-Checking To These Methods:

@Override
public boolean addAll(Collection<? extends E> c) {
	//return super.addAll(c);
	return false;
}
@Override
public boolean addAll(int index, Collection<? extends E> c) {
	//return super.addAll(index, c);
	return false;
}
@Override
public boolean removeAll(Collection<?> c) {
	//return super.removeAll(c);
	return false;
}
@Override
public boolean removeIf(Predicate<? super E> filter) {
	//return super.removeIf(filter);
	return false;
}
@Override
protected void removeRange(int fromIndex, int toIndex) {
	//super.removeRange(fromIndex, toIndex);
}
@Override
public void replaceAll(UnaryOperator<E> operator) {
	//super.replaceAll(operator);
}
@Override
public boolean retainAll(Collection<?> c) {
	//return super.retainAll(c);
	return false;
}
@Override
public boolean containsAll(Collection<?> c) {
	//return super.containsAll(c);
	return false;
}
@Override
public void sort(Comparator<? super E> c) {
	//super.sort(c);
}

//Iterators. These are only provided to maximize compatibility. Please use a standard for statement instead!

//Better Iterator Class:
public ChuIterator<E> chuIterator() { return new ChuIterator<E>(this); }

@Override
public Iterator<E> iterator() { return super.iterator(); }
@Override
public ListIterator<E> listIterator() { return super.listIterator(); }
@Override
public ListIterator<E> listIterator(int index) { return super.listIterator(index); }
@Override
public Spliterator<E> spliterator() { return super.spliterator(); }
}

//Here's a better Iterator class to match:
class ChuIterator<E> implements Iterator<E> {
public final ChuList<E> list;
public int index = -1;

ChuIterator(ChuList<E> l) { list = l; }

@Override
public boolean hasNext() {
	return index < list.size()-1;
}

@Override
public E next() {
	index++; E item = list.get(index); if(item==null)
		throw new NoSuchElementException(); return item;
}

public void goBack() {
	if(index > -1) index--;
}

public E last() {
	return list.get(index-1);
}

public E lookAhead() {
	return lookAhead(1);
}
public E lookAhead(int by) {
	if(by < 1) return null;
	return list.get(index+by);
}
}

//It's not like the Java people could handle this problem their ******* selves! The little ************* **** ********* *****!!!!!
//I SWEAR I'M GONNA *************************************************************************
//[NO SIGNAL SCREEN] The following program has been terminated for foul language. Please stand by.