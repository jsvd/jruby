/*
 * 
 */

package org.jruby.ext.ffi;


import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * A type which represents a conversion to/from a native type.
 */
@JRubyClass(name="FFI::Type::Mapped", parent="FFI::Type")
public final class MappedType extends Type {
    private final Type realType;
    private final IRubyObject converter;
    private final DynamicMethod toNativeMethod, fromNativeMethod;
    private final boolean isReferenceRequired;

    public static RubyClass createConverterTypeClass(Ruby runtime, RubyModule ffiModule) {
        RubyClass convClass = ffiModule.getClass("Type").defineClassUnder("Mapped", ffiModule.getClass("Type"),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        convClass.defineAnnotatedMethods(MappedType.class);
        convClass.defineAnnotatedConstants(MappedType.class);


        return convClass;
    }

    private MappedType(Ruby runtime, RubyClass klass, Type nativeType, IRubyObject converter,
            DynamicMethod toNativeMethod, DynamicMethod fromNativeMethod, boolean isRefererenceRequired) {
        super(runtime, klass, NativeType.MAPPED, nativeType.getNativeSize(), nativeType.getNativeAlignment());
        this.realType = nativeType;
        this.converter = converter;
        this.toNativeMethod = toNativeMethod;
        this.fromNativeMethod = fromNativeMethod;
        this.isReferenceRequired = isRefererenceRequired;
    }

    @JRubyMethod(name = "new", meta = true)
    public static final IRubyObject newMappedType(ThreadContext context, IRubyObject klass, IRubyObject converter) {
        if (!converter.respondsTo("native_type")) {
            throw context.runtime.newNoMethodError("converter needs a native_type method", "native_type", converter.getMetaClass());
        }
        
        DynamicMethod toNativeMethod = converter.getMetaClass().searchMethod("to_native");
        if (toNativeMethod.isUndefined()) {
            throw context.runtime.newNoMethodError("converter needs a to_native method", "to_native", converter.getMetaClass());
        }

        if (!Arity.TWO_ARGUMENTS.equals(toNativeMethod.getArity())) {
            throw context.runtime.newArgumentError("to_native should accept two arguments");
        }

        DynamicMethod fromNativeMethod = converter.getMetaClass().searchMethod("from_native");
        if (fromNativeMethod.isUndefined()) {
            throw context.runtime.newNoMethodError("converter needs a from_native method", "from_native", converter.getMetaClass());
        }

        if (!Arity.TWO_ARGUMENTS.equals(fromNativeMethod.getArity())) {
            throw context.runtime.newArgumentError("from_native should accept two arguments");
        }

        Type nativeType;
        try {
            nativeType = (Type) converter.callMethod(context, "native_type");
        } catch (ClassCastException ex) {
            throw context.runtime.newTypeError("native_type did not return instance of FFI::Type");
        }

        boolean isReferenceRequired;
        if (converter.respondsTo("reference_required?")) {
            isReferenceRequired = converter.callMethod(context, "reference_required?").isTrue();

        } else {
            switch (nativeType.nativeType) {
                case BOOL:
                case CHAR:
                case UCHAR:
                case SHORT:
                case USHORT:
                case INT:
                case UINT:
                case LONG:
                case ULONG:
                case LONG_LONG:
                case ULONG_LONG:
                case FLOAT:
                case DOUBLE:
                    isReferenceRequired = false;
                    break;

                default:
                    isReferenceRequired = true;
                    break;
            }
        }
        return new MappedType(context.runtime, (RubyClass) klass, nativeType, converter,
                toNativeMethod, fromNativeMethod, isReferenceRequired);
    }
    
    public final Type getRealType() {
        return realType;
    }

    public final boolean isReferenceRequired() {
        return isReferenceRequired;
    }

    public final boolean isPostInvokeRequired() {
        return false;
    }

    @JRubyMethod
    public final IRubyObject native_type(ThreadContext context) {
        return realType;
    }

    @JRubyMethod
    public final IRubyObject from_native(ThreadContext context, IRubyObject value, IRubyObject ctx) {
        return fromNative(context, value);
    }

    @JRubyMethod
    public final IRubyObject to_native(ThreadContext context, IRubyObject value, IRubyObject ctx) {
        return toNative(context, value);
    }


    public final IRubyObject fromNative(ThreadContext context, IRubyObject value) {
        return fromNativeMethod.call(context, converter, converter.getMetaClass(),
                "from_native", value, context.runtime.getNil());
    }

    public final IRubyObject toNative(ThreadContext context, IRubyObject value) {
        return toNativeMethod.call(context, converter, converter.getMetaClass(),
                "to_native", value, context.runtime.getNil());
    }
}
