package com.coffeestorm.dsl.sheetmagic.ui.hover;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ui.editor.hover.html.DefaultEObjectHoverProvider;

import com.coffeestorm.dsl.sheetmagic.sheetmagic.AreaMapping;
import com.coffeestorm.dsl.sheetmagic.utils.Utils;

public class SheetMagicHoverProvider extends DefaultEObjectHoverProvider {
	
	@Override
	protected String getHoverInfoAsHtml(EObject o) {
		if (o instanceof AreaMapping) {
			AreaMapping amap = (AreaMapping) o;
			StringBuffer buffer = new StringBuffer();
			buffer.append("<p>");
			buffer.append(getFirstLine(o)).append("</p>");
			
			buffer.append("<b>[Start Row]</b>: ").append(String.valueOf(amap.getStartRow())).append("<br>");
			buffer.append("<b>[Start Col]</b>: ").append(String.valueOf(getStartColText(amap.getStartCol()))).append("<br>");
			buffer.append("<b>[Num Rows]</b>: ").append(String.valueOf(amap.getNumRows())).append("<br>");
			buffer.append("<b>[Num Cols]</b>: ").append(String.valueOf(amap.getNumCols())).append("<br>");
			return buffer.toString();
		}
		return super.getHoverInfoAsHtml(o);
	}
	
	private String getStartColText(String startCol) {
		if (startCol.matches("\\d+")) {
			// positive integer
			return startCol;
		}
		// otherwise try to convert
		if (startCol.matches("[a-zA-Z]+")) {
			// letter column id
			return startCol + " <i>(" + String.valueOf(Utils.convertColumnLetterID2IntegerIndex(startCol)) + ")</i>";
		}
		
		return "<invalid id format>";
	}

}
