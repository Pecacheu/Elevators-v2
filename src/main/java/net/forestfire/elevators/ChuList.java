//ChuList v1.9.1, ©2024 Pecacheu. Licensed under GNU GPL 3.0
//An actually properly implemented List class? With error checking!? Imagine that!

package net.forestfire.elevators;

import java.util.*;

public class ChuList<E> extends ArrayList<E> {
public int length=0; //<- JavaScript Compatibility (Maybe not the best idea, though)
public ChuList() { super(); }
public ChuList(int initialCapacity) { super(initialCapacity); }
public ChuList(Collection<? extends E> c) { super(c); length=super.size(); }

//Construct with initial elements
@SafeVarargs
public ChuList(E... elements) {
	super(); super.addAll(Arrays.asList(elements));
	length = super.size();
}

@Override
public boolean add(E item) {
	super.add(item); length = super.size(); return true;
}

//JavaScript Compatibility
public void push(E item) { add(item); }

//Now, this is just common-sense functionality
public String join(String sep) {
	StringBuilder sb = new StringBuilder();
	for(int i=0, l=super.size(); i<l; ++i) sb.append(i==0?"":sep).append(super.get(i));
	return sb.toString();
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

//Actually working toString for debugging
@Override
public String toString() { return "["+this.join(", ")+"]"; }

public ChuList<E> addAll(int index, E[] arr) {
	for(int i=0,l=arr.length; i<l; ++i) add(index+i, arr[i]);
	return this;
}

public ChuList<E> addAll(E[] arr) {
    for(E e: arr) super.add(e); return this;
}

public ChuList<E> addAll(ArrayList<E> arr) {
    for(E e: arr) super.add(e); return this;
}

@Override
public ChuList<E> subList(int fromIndex, int toIndex) {
	if(fromIndex < 0) fromIndex = 0; if(toIndex > super.size()) toIndex = super.size();
	List<E> list; if(fromIndex > toIndex) list = super.subList(toIndex, fromIndex);
	else list = super.subList(fromIndex, toIndex); return new ChuList<E>(list);
}

//Iterators. These are only provided to maximize compatibility. Please use a standard for statement instead!

//Better Iterator Class
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

//Here's a better Iterator class to match
class ChuIterator<E> implements Iterator<E> {
public final ChuList<E> list;
public int index = -1;

ChuIterator(ChuList<E> l) { list = l; }

@Override
public boolean hasNext() { return index < list.size()-1; }

@Override
public E next() {
	index++; E item = list.get(index); if(item==null)
		throw new NoSuchElementException(); return item;
}

public void goBack() { if(index > -1) index--; }
public E last() { return list.get(index-1); }
public E lookAhead() { return lookAhead(1); }
public E lookAhead(int by) {
	if(by < 1) return null; return list.get(index+by);
}
}

//It's not like the Java people could handle this problem their ******* selves! The little ************* **** ********* *****!!!!!
//I SWEAR I'M GONNA *************************************************************************
//[NO SIGNAL SCREEN] The following program has been terminated for foul language. Please stand by.