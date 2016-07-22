//This work is licensed under a GNU General Public License. Visit http://gnu.org/licenses/gpl-3.0-standalone.html for details.
//RaichuList v1.4, Copyright (©) 2016, Pecacheu (Bryce Peterson, bbryce.com).

//An actually properly implemented List class? With error checking!? Imagine that!

package com.pecacheu.elevators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@SuppressWarnings("serial")
public class ChuList<E> extends ArrayList<E> {
	public int length = 0; //<- JavaScript Compatibility (Maybe not the best idea, though)
	
	public ChuList() {
		super();
	}
	
	public ChuList(int initialCapacity) {
		super(initialCapacity);
	}
	
	public ChuList(Collection<? extends E> c) {
		super(c); length = super.size();
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
	@Override
	public ChuList<E> subList(int fromIndex, int toIndex) {
		//ChuList<E> list = (ChuList<E>)super.subList(fromIndex, toIndex);
		//list.length = list.size(); return list;
		return null;
	}
	
	//Iterators. These are only provided to maximize compatibility. Please use a standard for statement instead!
	
	@Override
	public Iterator<E> iterator() { return super.iterator(); }
	@Override
	public ListIterator<E> listIterator() { return super.listIterator(); }
	@Override
	public ListIterator<E> listIterator(int index) { return super.listIterator(index); }
	@Override
	public Spliterator<E> spliterator() { return super.spliterator(); }
}

//It's not like the Java people could handle this problem their ******* selves! The little ************* **** ********* *****!!!!!
//I SWEAR I'M GONNA *************************************************************************
//[NO SIGNAL SCREEN] The following program has been terminated for foul language. Please stand by.