/**
 * Copyright 2010 Gerhard Aigner
 * 
 * This file is part of BRISS.
 * 
 * BRISS is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * BRISS is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * BRISS. If not, see http://www.gnu.org/licenses/.
 */
package at.laborg.briss.utils;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageNumberParser {
	private PageNumberParser() {
	}

	/**
	 * Page-number-range parser.  See EXCLUDE_PAGES_DESCRIPTION for behavior/spec.
	 *
	 * @param input
	 *            String to be parsed.  Presumed to contain a sequence of page-number-ranges.
	 * @param pageCount
	 *            # of pages in document (used to convert negative pagenums into offset-from-end-of-doc values)
	 * @return
	 * @throws ParseException
	 */
	public static Set<Integer> parsePageNumberRanges(final String input, final int pageCount)
			throws ParseException {

		// System.out.println("I: "+input);
		Set<Integer> rv = new HashSet<>();
		for ( String range : input.split(";+") ) {
			// System.out.println("R: "+range);
			final String pgnxs[] = range.split(",");
			Integer min, max = null;
			switch( pgnxs.length ) {
				default: throw new ParseException("range '"+range+"' returned > 2 splits", 0 );
				case 0:  throw new ParseException("range '"+range+"' returned 0 splits", 0 );
				case 2:  max = pgnumConv( pgnxs[1], pageCount ); // fall thru
				case 1:  min = pgnumConv( pgnxs[0], pageCount );
					 break;
			}
			max = max != null ? max : min;
			if( max < min ) { throw new ParseException("range '"+range+"' violates m<=n requirement: "+min+" > "+max, 0 ); }
			for( int ix = min; ix <= max; ++ix ) {
				// System.out.println("X: "+ix);
				rv.add( ix );
			}
		}
		return rv;
	}

	private static Integer pgnumConv( String pgNumIn, final int pageCount )
			throws ParseException {
		String inp = pgNumIn.trim();
		int num = Integer.parseInt(inp);
		if( num < 0 ) { // negative numbers ref last pages: -1 is last page, -2 is next to last page, etc.
			num = pageCount + num + 1;
		}
		if( num < 1 )         { throw new ParseException("pgNumIn '"+inp+"' ("+num+") is < 1", 0 ); }
		if( num > pageCount ) { throw new ParseException("pgNumIn '"+inp+"' ("+num+") is > "+pageCount, 0 ); }
		return num;
	}

}
