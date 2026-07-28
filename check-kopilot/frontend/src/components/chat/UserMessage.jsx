export default function UserMessage({ text }) {
  return (
    <div className="flex justify-end">
      <div className="max-w-lg rounded-2xl rounded-tr-sm bg-accent-600 px-4 py-2.5 text-lg text-white lg:max-w-xl lg:px-5 lg:py-3 lg:text-xl">
        {text}
      </div>
    </div>
  );
}
