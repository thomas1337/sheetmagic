/*
 * generated by Xtext 2.25.0
 */
package com.coffeestorm.dsl.sheetmagic.ide;

import com.coffeestorm.dsl.sheetmagic.SheetmagicRuntimeModule;
import com.coffeestorm.dsl.sheetmagic.SheetmagicStandaloneSetup;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.xtext.util.Modules2;

/**
 * Initialization support for running Xtext languages as language servers.
 */
public class SheetmagicIdeSetup extends SheetmagicStandaloneSetup {

	@Override
	public Injector createInjector() {
		return Guice.createInjector(Modules2.mixin(new SheetmagicRuntimeModule(), new SheetmagicIdeModule()));
	}
	
}